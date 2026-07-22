package org.ciyam.at;

import org.ciyam.at.test.ExecutableTest;
import org.ciyam.at.test.TestAPI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for platform-defined pricing of the built-in hashing functions.
 * <p>
 * With no platform override, hashing functions must cost exactly the same as any other function call.
 */
public class HashingFunctionStepTests extends ExecutableTest {

	private static final int DATA_START_ADDRESS = 2;
	private static final int DATA_BYTE_LENGTH = 16;

	@Test
	public void testIsHashingFunctionClassification() {
		FunctionCode[] hashingFunctions = new FunctionCode[] {
				FunctionCode.MD5_INTO_B, FunctionCode.CHECK_MD5_WITH_B,
				FunctionCode.RMD160_INTO_B, FunctionCode.CHECK_RMD160_WITH_B,
				FunctionCode.SHA256_INTO_B, FunctionCode.CHECK_SHA256_WITH_B,
				FunctionCode.HASH160_INTO_B, FunctionCode.CHECK_HASH160_WITH_B };

		for (FunctionCode hashingFunction : hashingFunctions)
			assertTrue(hashingFunction.name() + " should be a hashing function", hashingFunction.isHashingFunction());

		for (FunctionCode functionCode : FunctionCode.values())
			if (functionCode.value < 0x0200 || functionCode.value > 0x0207)
				assertFalse(functionCode.name() + " should not be a hashing function", functionCode.isHashingFunction());
	}

	@Test
	public void testDefaultHashingPricingIsUnchanged() {
		addHashingCode(FunctionCode.SHA256_INTO_B);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());
		// SET_VAL + SET_VAL + EXT_FUN_DAT_2 + STP_IMD
		assertEquals(1 + 1 + TestAPI.STEPS_PER_FUNCTION_CALL + 1, state.getSteps());
	}

	@Test
	public void testHashingHookReceivesFunctionCodeAndState() {
		int hashingSteps = 77;
		HashingPricingTestAPI pricingApi = new HashingPricingTestAPI(hashingSteps);
		api = pricingApi;
		long initialBalance = api.accounts.get(TestAPI.AT_ADDRESS).balance;

		addHashingCode(FunctionCode.SHA256_INTO_B);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());
		assertEquals(OpCode.EXT_FUN_DAT_2, pricingApi.pricedOpCode);
		assertSame(FunctionCode.SHA256_INTO_B, pricingApi.pricedFunctionCode);
		assertSame(state, pricingApi.pricedState);
		assertEquals(1, pricingApi.hashingPricings);
		assertEquals(1 + 1 + hashingSteps + 1, state.getSteps());
		assertEquals(initialBalance - 1 - 1 - hashingSteps - 1, api.accounts.get(TestAPI.AT_ADDRESS).balance);
	}

	@Test
	public void testHashingOverrideAppliesToEveryHashingFunction() {
		FunctionCode[] hashingFunctions = new FunctionCode[] {
				FunctionCode.MD5_INTO_B, FunctionCode.RMD160_INTO_B,
				FunctionCode.SHA256_INTO_B, FunctionCode.HASH160_INTO_B };

		int hashingSteps = 20;
		HashingPricingTestAPI pricingApi = new HashingPricingTestAPI(hashingSteps);
		api = pricingApi;

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(DATA_START_ADDRESS);
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(1).putLong(DATA_BYTE_LENGTH);
		for (FunctionCode hashingFunction : hashingFunctions)
			codeByteBuffer.put(OpCode.EXT_FUN_DAT_2.value).putShort(hashingFunction.value).putInt(0).putInt(1);
		codeByteBuffer.put(OpCode.STP_IMD.value);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());
		assertEquals(hashingFunctions.length, pricingApi.hashingPricings);
		assertEquals(1 + 1 + hashingFunctions.length * hashingSteps + 1, state.getSteps());
	}

	@Test
	public void testHashingOverrideDoesNotAffectOtherFunctions() {
		int hashingSteps = 77;
		HashingPricingTestAPI pricingApi = new HashingPricingTestAPI(hashingSteps);
		api = pricingApi;

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(DATA_START_ADDRESS);
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(1).putLong(DATA_BYTE_LENGTH);
		// Non-hashing function call should still be charged the ordinary flat cost
		codeByteBuffer.put(OpCode.EXT_FUN.value).putShort(FunctionCode.SWAP_A_AND_B.value);
		codeByteBuffer.put(OpCode.EXT_FUN_DAT_2.value).putShort(FunctionCode.SHA256_INTO_B.value).putInt(0).putInt(1);
		codeByteBuffer.put(OpCode.STP_IMD.value);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());
		assertEquals(1, pricingApi.hashingPricings);
		assertEquals(1 + 1 + TestAPI.STEPS_PER_FUNCTION_CALL + hashingSteps + 1, state.getSteps());
	}

	@Test
	public void testGeneralFunctionPricingOverrideStillAppliesToHashing() {
		// Platforms that only override the general external-function overload (like existing consumers)
		// must still have that override consulted for hashing functions, via default delegation.
		int functionSteps = 33;
		GeneralPricingTestAPI pricingApi = new GeneralPricingTestAPI(functionSteps);
		api = pricingApi;

		addHashingCode(FunctionCode.MD5_INTO_B);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());
		assertEquals(FunctionCode.MD5_INTO_B.value, pricingApi.pricedFunctionCode);
		assertEquals(1 + 1 + functionSteps + 1, state.getSteps());
	}

	private void addHashingCode(FunctionCode hashingFunction) {
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(DATA_START_ADDRESS);
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(1).putLong(DATA_BYTE_LENGTH);
		codeByteBuffer.put(OpCode.EXT_FUN_DAT_2.value).putShort(hashingFunction.value).putInt(0).putInt(1);
		codeByteBuffer.put(OpCode.STP_IMD.value);
	}

	private static class HashingPricingTestAPI extends TestAPI {
		private final int hashingSteps;
		private OpCode pricedOpCode;
		private FunctionCode pricedFunctionCode;
		private MachineState pricedState;
		private int hashingPricings;

		private HashingPricingTestAPI(int hashingSteps) {
			this.hashingSteps = hashingSteps;
		}

		@Override
		public int getHashingFunctionSteps(OpCode opcode, FunctionCode functionCode, MachineState state) {
			this.pricedOpCode = opcode;
			this.pricedFunctionCode = functionCode;
			this.pricedState = state;
			++this.hashingPricings;
			return this.hashingSteps;
		}
	}

	private static class GeneralPricingTestAPI extends TestAPI {
		private final int functionSteps;
		private short pricedFunctionCode;

		private GeneralPricingTestAPI(int functionSteps) {
			this.functionSteps = functionSteps;
		}

		@Override
		public int getOpCodeSteps(OpCode opcode, short rawFunctionCode, MachineState state) {
			this.pricedFunctionCode = rawFunctionCode;
			return this.functionSteps;
		}
	}

}
