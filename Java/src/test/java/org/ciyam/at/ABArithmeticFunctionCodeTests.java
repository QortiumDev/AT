package org.ciyam.at;

import static org.junit.Assert.*;

import org.ciyam.at.test.ExecutableTest;
import org.ciyam.at.test.TestUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the 256-bit A/B arithmetic function codes (0x0140 - 0x0147).
 * <p>
 * A1..A4 / B1..B4 are treated as 256-bit unsigned integers, least significant register first.
 * <p>
 * These function codes require AT creation version 3, so tests here run under a version 3 header,
 * apart from the explicit version 2 fault tests.
 */
public class ABArithmeticFunctionCodeTests extends ExecutableTest {

	private static final int A_SOURCE_ADDRESS = 0;
	private static final int B_SOURCE_ADDRESS = 4;
	private static final int A_RESULT_ADDRESS = 8;
	private static final int B_RESULT_ADDRESS = 12;

	private static final long ALL_ONES = 0xffffffffffffffffL;
	private static final long TOP_BIT = 0x8000000000000000L;

	@Before
	public void useVersion3Header() {
		headerBytes = TestUtils.V3_HEADER_BYTES;
	}

	@Test
	public void testAddAToB() throws ExecutionException {
		// B = B + A
		executeFunction(FunctionCode.ADD_A_TO_B,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 5L, 0L, 0L, 0L });

		assertA("A should be unmodified", 3L, 0L, 0L, 0L);
		assertB("B should hold sum", 8L, 0L, 0L, 0L);
	}

	@Test
	public void testAddBToA() throws ExecutionException {
		// A = A + B
		executeFunction(FunctionCode.ADD_B_TO_A,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 5L, 0L, 0L, 0L });

		assertA("A should hold sum", 8L, 0L, 0L, 0L);
		assertB("B should be unmodified", 5L, 0L, 0L, 0L);
	}

	@Test
	public void testAddCarriesAcrossAllLimbs() throws ExecutionException {
		// B is 2^192 - 1 so adding 1 carries through limbs 1, 2 and 3 into limb 4
		executeFunction(FunctionCode.ADD_A_TO_B,
				new long[] { 1L, 0L, 0L, 0L },
				new long[] { ALL_ONES, ALL_ONES, ALL_ONES, 0L });

		assertB("carry should propagate to top limb", 0L, 0L, 0L, 1L);
	}

	@Test
	public void testAddWrapsModulo2To256() throws ExecutionException {
		// B is 2^256 - 1 so adding 5 wraps to 4
		executeFunction(FunctionCode.ADD_A_TO_B,
				new long[] { 5L, 0L, 0L, 0L },
				new long[] { ALL_ONES, ALL_ONES, ALL_ONES, ALL_ONES });

		assertB("sum should wrap modulo 2^256", 4L, 0L, 0L, 0L);
	}

	@Test
	public void testSubAFromB() throws ExecutionException {
		// B = B - A
		executeFunction(FunctionCode.SUB_A_FROM_B,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 5L, 0L, 0L, 0L });

		assertA("A should be unmodified", 3L, 0L, 0L, 0L);
		assertB("B should hold difference", 2L, 0L, 0L, 0L);
	}

	@Test
	public void testSubBFromA() throws ExecutionException {
		// A = A - B
		executeFunction(FunctionCode.SUB_B_FROM_A,
				new long[] { 5L, 0L, 0L, 0L },
				new long[] { 3L, 0L, 0L, 0L });

		assertA("A should hold difference", 2L, 0L, 0L, 0L);
		assertB("B should be unmodified", 3L, 0L, 0L, 0L);
	}

	@Test
	public void testSubBorrowsAcrossAllLimbs() throws ExecutionException {
		// B is 2^192 so subtracting 1 borrows through limbs 3, 2 and 1
		executeFunction(FunctionCode.SUB_A_FROM_B,
				new long[] { 1L, 0L, 0L, 0L },
				new long[] { 0L, 0L, 0L, 1L });

		assertB("borrow should propagate from top limb", ALL_ONES, ALL_ONES, ALL_ONES, 0L);
	}

	@Test
	public void testSubWrapsModulo2To256() throws ExecutionException {
		// 0 - 1 should wrap to 2^256 - 1
		executeFunction(FunctionCode.SUB_A_FROM_B,
				new long[] { 1L, 0L, 0L, 0L },
				new long[] { 0L, 0L, 0L, 0L });

		assertB("difference should wrap modulo 2^256", ALL_ONES, ALL_ONES, ALL_ONES, ALL_ONES);
	}

	@Test
	public void testMulAByB() throws ExecutionException {
		// B = A * B
		executeFunction(FunctionCode.MUL_A_BY_B,
				new long[] { 6L, 0L, 0L, 0L },
				new long[] { 7L, 0L, 0L, 0L });

		assertA("A should be unmodified", 6L, 0L, 0L, 0L);
		assertB("B should hold product", 42L, 0L, 0L, 0L);
	}

	@Test
	public void testMulBByA() throws ExecutionException {
		// A = A * B
		executeFunction(FunctionCode.MUL_B_BY_A,
				new long[] { 6L, 0L, 0L, 0L },
				new long[] { 7L, 0L, 0L, 0L });

		assertA("A should hold product", 42L, 0L, 0L, 0L);
		assertB("B should be unmodified", 7L, 0L, 0L, 0L);
	}

	@Test
	public void testMulCarriesAcrossLimbs() throws ExecutionException {
		// 2^64 * 2^64 = 2^128, i.e. limb 3
		executeFunction(FunctionCode.MUL_A_BY_B,
				new long[] { 0L, 1L, 0L, 0L },
				new long[] { 0L, 1L, 0L, 0L });

		assertB("product should reach limb 3", 0L, 0L, 1L, 0L);
	}

	@Test
	public void testMulWrapsModulo2To256() throws ExecutionException {
		// 3 * 2^255 = 2^256 + 2^255, and only the low 256 bits (2^255) are kept
		executeFunction(FunctionCode.MUL_A_BY_B,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 0L, 0L, 0L, TOP_BIT });

		assertB("product should wrap modulo 2^256", 0L, 0L, 0L, TOP_BIT);
	}

	@Test
	public void testDivAByB() throws ExecutionException {
		// B = A / B, with unsigned truncating division
		executeFunction(FunctionCode.DIV_A_BY_B,
				new long[] { 7L, 0L, 0L, 0L },
				new long[] { 2L, 0L, 0L, 0L });

		assertA("A should be unmodified", 7L, 0L, 0L, 0L);
		assertB("B should hold truncated quotient", 3L, 0L, 0L, 0L);
	}

	@Test
	public void testDivBByA() throws ExecutionException {
		// A = B / A, with unsigned truncating division
		executeFunction(FunctionCode.DIV_B_BY_A,
				new long[] { 2L, 0L, 0L, 0L },
				new long[] { 7L, 0L, 0L, 0L });

		assertA("A should hold truncated quotient", 3L, 0L, 0L, 0L);
		assertB("B should be unmodified", 7L, 0L, 0L, 0L);
	}

	@Test
	public void testDivQuotientSpansLimbs() throws ExecutionException {
		// 2^192 / 2 = 2^191, i.e. top bit of limb 3
		executeFunction(FunctionCode.DIV_A_BY_B,
				new long[] { 0L, 0L, 0L, 1L },
				new long[] { 2L, 0L, 0L, 0L });

		assertB("quotient should span limb boundary", 0L, 0L, TOP_BIT, 0L);
	}

	@Test
	public void testDivIsUnsigned() throws ExecutionException {
		// A is 2^255, which is negative if interpreted as signed, so unsigned division must give 2^254
		executeFunction(FunctionCode.DIV_A_BY_B,
				new long[] { 0L, 0L, 0L, TOP_BIT },
				new long[] { 2L, 0L, 0L, 0L });

		assertB("division should be unsigned", 0L, 0L, 0L, 0x4000000000000000L);
	}

	@Test
	public void testDivAByZeroBIsFatalError() throws ExecutionException {
		executeFunction(FunctionCode.DIV_A_BY_B,
				new long[] { 7L, 0L, 0L, 0L },
				new long[] { 0L, 0L, 0L, 0L });

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
	}

	@Test
	public void testDivBByZeroAIsFatalError() throws ExecutionException {
		executeFunction(FunctionCode.DIV_B_BY_A,
				new long[] { 0L, 0L, 0L, 0L },
				new long[] { 7L, 0L, 0L, 0L });

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
	}

	@Test
	public void testAddAToBIsFatalErrorForVersion2() throws ExecutionException {
		// Version 2 ATs must treat 0x0140 like an unknown function code
		headerBytes = TestUtils.HEADER_BYTES;

		executeFunction(FunctionCode.ADD_A_TO_B,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 5L, 0L, 0L, 0L });

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());

		// Execution must have faulted before reaching GET_A_DAT / GET_B_DAT, so result addresses remain zero
		assertEquals(0L, getData(A_RESULT_ADDRESS));
		assertEquals(0L, getData(B_RESULT_ADDRESS));
	}

	@Test
	public void testAddBToAIsFatalErrorForVersion2() throws ExecutionException {
		// Version 2 ATs must treat 0x0141 like an unknown function code
		headerBytes = TestUtils.HEADER_BYTES;

		executeFunction(FunctionCode.ADD_B_TO_A,
				new long[] { 3L, 0L, 0L, 0L },
				new long[] { 5L, 0L, 0L, 0L });

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());

		// Execution must have faulted before reaching GET_A_DAT / GET_B_DAT, so result addresses remain zero
		assertEquals(0L, getData(A_RESULT_ADDRESS));
		assertEquals(0L, getData(B_RESULT_ADDRESS));
	}

	@Test
	public void testAllArithmeticFunctionCodesAreFatalErrorForVersion2() throws ExecutionException {
		FunctionCode[] functionCodes = new FunctionCode[] {
				FunctionCode.ADD_A_TO_B, FunctionCode.ADD_B_TO_A,
				FunctionCode.SUB_A_FROM_B, FunctionCode.SUB_B_FROM_A,
				FunctionCode.MUL_A_BY_B, FunctionCode.MUL_B_BY_A,
				FunctionCode.DIV_A_BY_B, FunctionCode.DIV_B_BY_A
		};

		for (FunctionCode functionCode : functionCodes) {
			// Fresh buffers/API for each function code
			beforeTest();
			headerBytes = TestUtils.HEADER_BYTES;

			executeFunction(functionCode,
					new long[] { 3L, 0L, 0L, 0L },
					new long[] { 5L, 0L, 0L, 0L });

			assertTrue(functionCode.name() + " should be fatal error under version 2", state.hadFatalError());
		}
	}

	/** Loads A and B registers with passed values, executes passed function, then saves A and B back into the data segment. */
	private void executeFunction(FunctionCode functionCode, long[] aValues, long[] bValues) {
		// A register source values
		for (long aValue : aValues)
			dataByteBuffer.putLong(aValue);

		// B register source values
		assertEquals(B_SOURCE_ADDRESS * MachineState.VALUE_SIZE, dataByteBuffer.position());
		for (long bValue : bValues)
			dataByteBuffer.putLong(bValue);

		codeByteBuffer.put(OpCode.EXT_FUN_VAL.value).putShort(FunctionCode.SET_A_DAT.value).putLong(A_SOURCE_ADDRESS);
		codeByteBuffer.put(OpCode.EXT_FUN_VAL.value).putShort(FunctionCode.SET_B_DAT.value).putLong(B_SOURCE_ADDRESS);
		codeByteBuffer.put(OpCode.EXT_FUN.value).putShort(functionCode.value);
		codeByteBuffer.put(OpCode.EXT_FUN_VAL.value).putShort(FunctionCode.GET_A_DAT.value).putLong(A_RESULT_ADDRESS);
		codeByteBuffer.put(OpCode.EXT_FUN_VAL.value).putShort(FunctionCode.GET_B_DAT.value).putLong(B_RESULT_ADDRESS);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);
	}

	private void assertA(String message, long expectedA1, long expectedA2, long expectedA3, long expectedA4) {
		assertFalse(state.hadFatalError());
		assertEquals(message + " (A1)", expectedA1, getData(A_RESULT_ADDRESS));
		assertEquals(message + " (A2)", expectedA2, getData(A_RESULT_ADDRESS + 1));
		assertEquals(message + " (A3)", expectedA3, getData(A_RESULT_ADDRESS + 2));
		assertEquals(message + " (A4)", expectedA4, getData(A_RESULT_ADDRESS + 3));
	}

	private void assertB(String message, long expectedB1, long expectedB2, long expectedB3, long expectedB4) {
		assertFalse(state.hadFatalError());
		assertEquals(message + " (B1)", expectedB1, getData(B_RESULT_ADDRESS));
		assertEquals(message + " (B2)", expectedB2, getData(B_RESULT_ADDRESS + 1));
		assertEquals(message + " (B3)", expectedB3, getData(B_RESULT_ADDRESS + 2));
		assertEquals(message + " (B4)", expectedB4, getData(B_RESULT_ADDRESS + 3));
	}

}
