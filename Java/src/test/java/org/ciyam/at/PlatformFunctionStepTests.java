package org.ciyam.at;

import org.ciyam.at.test.ExecutableTest;
import org.ciyam.at.test.TestAPI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlatformFunctionStepTests extends ExecutableTest {

	private static final short PLATFORM_FUNCTION_CODE = 0x0501;

	@Test
	public void testDefaultExternalFunctionPricingIsUnchanged() {
		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(PLATFORM_FUNCTION_CODE).putInt(0);
		codeByteBuffer.put(OpCode.STP_IMD.value);

		execute(true);

		assertTrue(state.isStopped());
		assertFalse(state.isFinished());
		assertFalse(state.hadFatalError());
		assertEquals(TestAPI.STEPS_PER_FUNCTION_CALL + 1, state.getSteps());
	}

	@Test
	public void testRawFunctionCodeAndStateReachPricingHook() {
		int functionSteps = 123;
		FunctionPricingTestAPI pricingApi = new FunctionPricingTestAPI(functionSteps);
		api = pricingApi;
		long initialBalance = api.accounts.get(TestAPI.AT_ADDRESS).balance;

		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(PLATFORM_FUNCTION_CODE).putInt(0);
		codeByteBuffer.put(OpCode.STP_IMD.value);

		execute(true);

		assertEquals(OpCode.EXT_FUN_DAT, pricingApi.pricedOpCode);
		assertEquals(PLATFORM_FUNCTION_CODE, pricingApi.pricedFunctionCode);
		assertSame(state, pricingApi.pricedState);
		assertEquals(1, pricingApi.functionExecutions);
		assertEquals(functionSteps + 1, state.getSteps());
		assertEquals(initialBalance - functionSteps - 1, api.accounts.get(TestAPI.AT_ADDRESS).balance);
	}

	@Test
	public void testFunctionSpecificCostSleepsBeforeExecution() {
		FunctionPricingTestAPI pricingApi = new FunctionPricingTestAPI(TestAPI.MAX_STEPS_PER_ROUND + 1);
		api = pricingApi;
		long initialBalance = api.accounts.get(TestAPI.AT_ADDRESS).balance;

		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(PLATFORM_FUNCTION_CODE).putInt(0);
		codeByteBuffer.put(OpCode.STP_IMD.value);

		execute(true);

		assertTrue(state.isSleeping());
		assertFalse(state.isFinished());
		assertFalse(state.hadFatalError());
		assertEquals(0, state.getProgramCounter());
		assertEquals(0, state.getSteps());
		assertEquals(0, pricingApi.functionExecutions);
		assertEquals(initialBalance, api.accounts.get(TestAPI.AT_ADDRESS).balance);
	}

	private static class FunctionPricingTestAPI extends TestAPI {
		private final int functionSteps;
		private OpCode pricedOpCode;
		private short pricedFunctionCode;
		private MachineState pricedState;
		private int functionExecutions;

		private FunctionPricingTestAPI(int functionSteps) {
			this.functionSteps = functionSteps;
		}

		@Override
		public int getOpCodeSteps(OpCode opcode, short rawFunctionCode, MachineState state) {
			this.pricedOpCode = opcode;
			this.pricedFunctionCode = rawFunctionCode;
			this.pricedState = state;
			return this.functionSteps;
		}

		@Override
		public void platformSpecificPostCheckExecute(FunctionData functionData, MachineState state,
				short rawFunctionCode) throws ExecutionException {
			++this.functionExecutions;
			super.platformSpecificPostCheckExecute(functionData, state, rawFunctionCode);
		}
	}
}
