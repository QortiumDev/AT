package org.ciyam.at;

import static org.ciyam.at.test.TestUtils.hexToBytes;
import static org.junit.Assert.*;

import java.util.Arrays;

import org.ciyam.at.test.ExecutableTest;
import org.ciyam.at.test.TestUtils;
import org.junit.Test;

public class SerializationTests extends ExecutableTest {

	@Test
	public void testIntToByteArray() {
		byte[] expectedBytes = hexToBytes("fedcba98");
		byte[] actualBytes = MachineState.toByteArray(0xfedcba98);

		assertTrue(Arrays.equals(expectedBytes, actualBytes));
	}

	@Test
	public void testLongToByteArray() {
		byte[] expectedBytes = hexToBytes("fedcba9876543210");
		byte[] actualBytes = MachineState.toByteArray(0xfedcba9876543210L);

		assertTrue(Arrays.equals(expectedBytes, actualBytes));
	}

	/** Test serialization of state with stop address. */
	@Test
	public void testPCS2() throws ExecutionException {
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).put(hexToBytes("0000000011111111"));

		codeByteBuffer.put(OpCode.SET_PCS.value);
		int expectedStopAddress = codeByteBuffer.position();

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).put(hexToBytes("0000000022222222"));

		codeByteBuffer.put(OpCode.FIN_IMD.value);

		simulate();

		assertEquals(expectedStopAddress, (int) state.getOnStopAddress());
		assertTrue(state.isFinished());
		assertFalse(state.hadFatalError());
	}

	/** Test serialization of state with data pushed onto user stack. */
	@Test
	public void testStopWithStacks() throws ExecutionException {
		long initialValue = 100L;
		long increment = 10L;

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(initialValue); // 0000

		codeByteBuffer.put(OpCode.SET_PCS.value); // 000d
		int expectedStopAddress = codeByteBuffer.position();

		codeByteBuffer.put(OpCode.JMP_SUB.value).putInt(0x002a); // 000e

		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(1).putLong(increment); // 0013

		codeByteBuffer.put(OpCode.ADD_DAT.value).putInt(0).putInt(1); // 0020

		codeByteBuffer.put(OpCode.STP_IMD.value); // 0029

		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(FunctionCode.ECHO.value).putInt(0); // 002a

		codeByteBuffer.put(OpCode.PSH_DAT.value).putInt(0); // 0031

		codeByteBuffer.put(OpCode.RET_SUB.value); // 0036

		byte[] savedState = simulate();

		assertEquals(expectedStopAddress, (int) state.getOnStopAddress());
		assertTrue(state.isStopped());
		assertFalse(state.hadFatalError());

		// Just after first STP_IMD we expect address 0 to be initialValue + increment
		long expectedValue = initialValue + increment;
		assertEquals(expectedValue, getData(0));

		// Perform another execution round
		savedState = continueSimulation(savedState);
		expectedValue += increment;
		assertEquals(expectedValue, getData(0));

		savedState = continueSimulation(savedState);
		expectedValue += increment;
		assertEquals(expectedValue, getData(0));
	}

	/** Test serialization to/from creation bytes. */
	@Test
	public void testCreationBytes() {
		byte[] headerBytes = TestUtils.HEADER_BYTES;
		byte[] codeBytes = codeByteBuffer.array();
		byte[] dataBytes = dataByteBuffer.array();

		state = new MachineState(api, loggerFactory, headerBytes, codeBytes, dataBytes);
		packedState = state.toBytes();

		byte[] creationBytes = MachineState.toCreationBytes(TestUtils.VERSION, codeBytes, dataBytes, TestUtils.NUM_CALL_STACK_PAGES, TestUtils.NUM_USER_STACK_PAGES, TestUtils.MIN_ACTIVATION_AMOUNT);

		MachineState restoredState = new MachineState(api, loggerFactory, creationBytes);
		byte[] packedRestoredSate = restoredState.toBytes();

		assertTrue(Arrays.equals(packedState, packedRestoredSate));

		restoredState = MachineState.fromBytes(api, loggerFactory, packedState, codeBytes);
	}

	/** Test serialization to/from creation bytes for version 3 ATs. */
	@Test
	public void testVersion3CreationBytes() {
		byte[] headerBytes = TestUtils.V3_HEADER_BYTES;
		byte[] codeBytes = codeByteBuffer.array();
		byte[] dataBytes = dataByteBuffer.array();

		state = new MachineState(api, loggerFactory, headerBytes, codeBytes, dataBytes);
		assertEquals(TestUtils.V3_VERSION, state.version);
		packedState = state.toBytes();

		byte[] creationBytes = MachineState.toCreationBytes(TestUtils.V3_VERSION, codeBytes, dataBytes, TestUtils.NUM_CALL_STACK_PAGES, TestUtils.NUM_USER_STACK_PAGES, TestUtils.MIN_ACTIVATION_AMOUNT);

		MachineState restoredState = new MachineState(api, loggerFactory, creationBytes);
		assertEquals(TestUtils.V3_VERSION, restoredState.version);
		byte[] packedRestoredSate = restoredState.toBytes();

		assertTrue(Arrays.equals(packedState, packedRestoredSate));

		restoredState = MachineState.fromBytes(api, loggerFactory, packedState, codeBytes);
		assertEquals(TestUtils.V3_VERSION, restoredState.version);
	}

	private byte[] simulate() {
		byte[] headerBytes = TestUtils.HEADER_BYTES;
		byte[] codeBytes = codeByteBuffer.array();
		byte[] dataBytes = new byte[0];

		state = new MachineState(api, loggerFactory, headerBytes, codeBytes, dataBytes);

		return executeAndCheck(state);
	}

	private byte[] continueSimulation(byte[] savedState) {
		byte[] codeBytes = codeByteBuffer.array();
		state = MachineState.fromBytes(api, loggerFactory, savedState, codeBytes);

		// Pretend we're on next block
		api.bumpCurrentBlockHeight();

		return executeAndCheck(state);
	}

	private byte[] executeAndCheck(MachineState state) {
		state.execute();

		// Fetch current state, and code bytes
		byte[] stateBytes = unwrapState(state);
		byte[] codeBytes = state.getCodeBytes();

		// Rebuild new MachineState using fetched state & bytes
		MachineState restoredState = MachineState.fromBytes(api, loggerFactory, stateBytes, codeBytes);
		// Extract rebuilt state and code bytes
		byte[] restoredStateBytes = restoredState.toBytes();
		byte[] restoredCodeBytes = state.getCodeBytes();

		// Check that both states and bytes match
		assertTrue("Serialization->Deserialization->Reserialization error", Arrays.equals(stateBytes, restoredStateBytes));
		assertTrue("Serialization->Deserialization->Reserialization error", Arrays.equals(codeBytes, restoredCodeBytes));

		// Check reuse works too
		byte[] dupStateBytes = new byte[stateBytes.length];
		System.arraycopy(stateBytes, 0, dupStateBytes, 0, stateBytes.length);

		restoredState.reuseFromBytes(api, dupStateBytes);
		byte[] restoredReusedStateBytes = restoredState.toBytes();
		assertTrue("Serialization->Deserialization->Reserialization reuse error", Arrays.equals(stateBytes, restoredReusedStateBytes));

		return stateBytes;
	}

}
