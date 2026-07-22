package org.ciyam.at;

import org.ciyam.at.test.ExecutableTest;
import org.ciyam.at.test.TestAPI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the platform-specific function code range (0x0500 - 0x06ff) being dispatched
 * to the API without this library enumerating the individual codes.
 */
public class PlatformPassthroughTests extends ExecutableTest {

	/** A platform function code, deep in the 0x06xx sub-range, that the library knows nothing about. */
	private static final short CUSTOM_PLATFORM_FUNCTION_CODE = 0x0677;

	@Test
	public void testPlatformRangeBounds() {
		assertFalse(FunctionCode.isPlatformFunctionCode(FunctionCode.PLATFORM_CODE_START - 1));
		assertTrue(FunctionCode.isPlatformFunctionCode(FunctionCode.PLATFORM_CODE_START));
		assertTrue(FunctionCode.isPlatformFunctionCode(0x0600));
		assertTrue(FunctionCode.isPlatformFunctionCode(FunctionCode.PLATFORM_CODE_END));
		assertFalse(FunctionCode.isPlatformFunctionCode(FunctionCode.PLATFORM_CODE_END + 1));
	}

	@Test
	public void testPlatformRangeMapsToApiPassthrough() {
		assertSame(FunctionCode.API_PASSTHROUGH, FunctionCode.valueOf(FunctionCode.PLATFORM_CODE_START));
		assertSame(FunctionCode.API_PASSTHROUGH, FunctionCode.valueOf(0x0600));
		assertSame(FunctionCode.API_PASSTHROUGH, FunctionCode.valueOf(FunctionCode.PLATFORM_CODE_END));

		assertNull(FunctionCode.valueOf(FunctionCode.PLATFORM_CODE_START - 1));
		assertNull(FunctionCode.valueOf(FunctionCode.PLATFORM_CODE_END + 1));
	}

	@Test
	public void testCustomPlatformFunctionIsDispatchedToApi() {
		PassthroughTestAPI passthroughApi = new PassthroughTestAPI();
		api = passthroughApi;

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(12345L);
		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(CUSTOM_PLATFORM_FUNCTION_CODE).putInt(0);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertFalse(state.hadFatalError());
		assertEquals(CUSTOM_PLATFORM_FUNCTION_CODE, passthroughApi.preCheckedFunctionCode);
		assertEquals(CUSTOM_PLATFORM_FUNCTION_CODE, passthroughApi.executedFunctionCode);
		assertEquals(12345L, passthroughApi.executedValue1);
	}

	@Test
	public void testCustomPlatformFunctionWrongSignatureIsFatalError() {
		PassthroughTestAPI passthroughApi = new PassthroughTestAPI();
		api = passthroughApi;

		// Our custom function takes one arg and returns no value, so EXT_FUN_RET is the wrong opcode for it
		codeByteBuffer.put(OpCode.EXT_FUN_RET.value).putShort(CUSTOM_PLATFORM_FUNCTION_CODE).putInt(0);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
		assertEquals(CUSTOM_PLATFORM_FUNCTION_CODE, passthroughApi.preCheckedFunctionCode);
		assertEquals(0, passthroughApi.executedFunctionCode);
	}

	/** TestAPI extended with a platform function code the library's enum does not contain. */
	private static class PassthroughTestAPI extends TestAPI {
		private short preCheckedFunctionCode;
		private short executedFunctionCode;
		private long executedValue1;

		@Override
		public void platformSpecificPreExecuteCheck(int paramCount, boolean returnValueExpected,
				MachineState state, short rawFunctionCode) throws IllegalFunctionCodeException {
			if (rawFunctionCode != CUSTOM_PLATFORM_FUNCTION_CODE) {
				super.platformSpecificPreExecuteCheck(paramCount, returnValueExpected, state, rawFunctionCode);
				return;
			}

			this.preCheckedFunctionCode = rawFunctionCode;

			// Takes one arg, no return value
			if (paramCount != 1 || returnValueExpected)
				throw new IllegalFunctionCodeException("Passed paramCount (" + paramCount + ") and returnValueExpected (" + returnValueExpected
						+ ") do not match platform-specific function code 0x" + String.format("%04x", rawFunctionCode));
		}

		@Override
		public void platformSpecificPostCheckExecute(FunctionData functionData, MachineState state,
				short rawFunctionCode) throws ExecutionException {
			if (rawFunctionCode != CUSTOM_PLATFORM_FUNCTION_CODE) {
				super.platformSpecificPostCheckExecute(functionData, state, rawFunctionCode);
				return;
			}

			this.executedFunctionCode = rawFunctionCode;
			this.executedValue1 = functionData.value1;
		}
	}

}
