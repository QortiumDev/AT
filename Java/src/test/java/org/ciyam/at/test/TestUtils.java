package org.ciyam.at.test;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.ciyam.at.MachineState;

public class TestUtils {

	public static final short VERSION = 2;
	public static final short V3_VERSION = 3;
	public static final short NUM_CODE_PAGES = 0x0200;
	public static final short NUM_DATA_PAGES = 0x0200;
	public static final short NUM_CALL_STACK_PAGES = 0x0010;
	public static final short NUM_USER_STACK_PAGES = 0x0010;
	public static final long MIN_ACTIVATION_AMOUNT = 0L;
	public static final byte[] HEADER_BYTES = toHeaderBytes(VERSION, NUM_CODE_PAGES, NUM_DATA_PAGES, NUM_CALL_STACK_PAGES, NUM_USER_STACK_PAGES, MIN_ACTIVATION_AMOUNT);
	public static final byte[] V3_HEADER_BYTES = toHeaderBytes(V3_VERSION, NUM_CODE_PAGES, NUM_DATA_PAGES, NUM_CALL_STACK_PAGES, NUM_USER_STACK_PAGES, MIN_ACTIVATION_AMOUNT);

	public static byte[] hexToBytes(String hex) {
		byte[] output = new byte[hex.length() / 2];
		byte[] converted = new BigInteger("00" + hex, 16).toByteArray();

		int convertedLength = Math.min(output.length, converted.length);
		int convertedStart = converted.length - convertedLength;

		int outputStart = output.length - convertedLength;

		System.arraycopy(converted, convertedStart, output, outputStart, convertedLength);

		return output;
	}

	public static byte[] toHeaderBytes(short version, short numCodePages, short numDataPages, short numCallStackPages, short numUserStackPages, long minActivationAmount) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(MachineState.HEADER_LENGTH);

		// Version
		byteBuffer.putShort(version);

		// Reserved
		byteBuffer.putShort((short) 0);

		// Code length
		byteBuffer.putShort(numCodePages);

		// Data length
		byteBuffer.putShort(numDataPages);

		// Call stack length
		byteBuffer.putShort(numCallStackPages);

		// User stack length
		byteBuffer.putShort(numUserStackPages);

		// Minimum activation amount
		byteBuffer.putLong(minActivationAmount);

		return byteBuffer.array();
	}

	public static byte[] getBytes(ByteBuffer byteBuffer) {
		byteBuffer.flip();
		byte[] bytes = new byte[byteBuffer.limit()];
		byteBuffer.get(bytes);
		byteBuffer.clear();
		return bytes;
	}

}
