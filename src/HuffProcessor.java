import java.util.*;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;
	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] ret = readForCounts(in);
		HuffNode tree = makeTreeFromCounts(ret);
		String[] codings = new String[PSEUDO_EOF + 1];
		codings = makeCodingsFromTree(tree, "", codings);
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(tree, out);
		in.reset();
		writeCompressedBits(codings, in, out);
	}
	
	/*
	 * counts the number of occurrence of every 8-bit sequence
	 */
	public int[] readForCounts(BitInputStream in){
		int[] ret = new int[PSEUDO_EOF];
		while (true){
	        int val = in.readBits(BITS_PER_WORD);
	        if (val == -1) break;
	        ret[val] += 1;
		}
		return ret;
	}
	
	/*
	 * uses a priority queue to help transform a forest of single
	 * nodes into a tree formed by the proper weights
	 */
	public HuffNode makeTreeFromCounts(int[] ret){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		int count = 0; //for analysis question 2
		for (int k = 0; k < ret.length; k++){
			if (ret[k] > 0){
				pq.add(new HuffNode(k, ret[k]));
				count++; //for analysis question 2
			}
			
		}
		System.out.println(count); //for analysis question 2
		pq.add(new HuffNode(PSEUDO_EOF, 1));
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.weight() + right.weight(), left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();		 //I have now transformed a forest of single nodes into one tree	
		return root;
	}
	
	/*
	 * uses the helper method, doWork, to create the codings
	 * (a bit sequence from the tree)
	 */
	public String[] makeCodingsFromTree(HuffNode root, String path, String[] codings){			//much of this code comes from leaf trails apt
		String[] array = new String[PSEUDO_EOF + 1];
		doWork(root, array,"");
		return array;
	}

	public void doWork(HuffNode tree, String[] array, String path) {
		if (tree == null) return;
		if (tree.left() == null && tree.right() == null) 
			array[tree.value()] = path;
		else {
			if (tree.left() != null)
				doWork(tree.left(),array,path+"0");
		doWork(tree.right(),array,path+"1");
		}
	}
	
	/*
	 *writes the bits for HUFF_TREE appropriately and
	 *writes the Huffman tree so that it can then be read
	 *using a preorder traversal
	 */
	public void writeHeader(HuffNode root, BitOutputStream out){
		if (root.left() == null && root.right() == null){																//confered with jun for these few lines
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.value());
			return;
		}
		if (root.value() == PSEUDO_EOF)
			return;
		else
			out.writeBits(1, 0);
		writeHeader(root.left(), out);
		writeHeader(root.right(), out);
		}
	
	/*
	 * reads every BITS_PER_WORD bit-sequence, finds the encoding,
	 * and writes the encoding as a bit sequence.
	 */
	public void writeCompressedBits(String[] array, BitInputStream in, BitOutputStream out) {
		int val;
		while(true) {
			val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			out.writeBits(array[val].length(), Integer.parseInt(array[val], 2));
		}
		out.writeBits(array[PSEUDO_EOF].length(), Integer.parseInt(array[PSEUDO_EOF], 2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
	   int tag = in.readBits(BITS_PER_INT);
	   if (tag != HUFF_TREE) throw new HuffException("wrong file");
	   HuffNode root = readTreeHeader(in);
	   readCompressedBits(in, out, root);
	}
	
	/*
	 * decompresses the compressed bits that follow the tag
	 */
	private void readCompressedBits(BitInputStream in, BitOutputStream out, HuffNode root) {
		HuffNode head = root;
		while (true){
			int current = in.readBits(1);
			if (current == -1) throw new HuffException("bad input, no PSUEDO_EOF");
			else{
				if (current == 0) head = head.left();	
				if (current == 1) head = head.right();
				if (head.left() == null && head.right() == null) {
					if (head.value() == PSEUDO_EOF)
						break;
					else{
						out.writeBits(BITS_PER_WORD,head.value());       // writeBits(9)-->8...this fixed my issue
						head = root;
					}	
				}
			}	
		}
	}
	
	/*
	 * returns a HuffNode that is the root of the Huffman tree
	 * used for uncompressing a file.
	*/
	public HuffNode readTreeHeader(BitInputStream in) {
		int current = in.readBits(1);
		if (current == 0){
		    HuffNode left = readTreeHeader(in);
		    HuffNode right = readTreeHeader(in);
		    return new HuffNode(-1,-1,left,right); 		// changed 1, 1 to -1, -1
		}
		return new HuffNode(in.readBits(BITS_PER_WORD + 1), -1); //changed 1 to -1
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+ myHeader);
    }
	
}