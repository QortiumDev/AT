package org.ciyam.at.test;

import java.nio.ByteBuffer;
import java.security.Security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ciyam.at.AtLoggerFactory;
import org.ciyam.at.MachineState;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public class ExecutableTest {

	public static final int DATA_OFFSET = MachineState.HEADER_LENGTH; // code bytes are not present
	public static final int CALL_STACK_OFFSET = DATA_OFFSET + TestUtils.NUM_DATA_PAGES * MachineState.VALUE_SIZE;

	public AtLoggerFactory loggerFactory;
	public TestAPI api;
	public MachineState state;
	public ByteBuffer codeByteBuffer;
	public ByteBuffer dataByteBuffer;
	public ByteBuffer stateByteBuffer;
	public int callStackSize;
	public int userStackOffset;
	public int userStackSize;
	public byte[] packedState;
	public byte[] codeBytes;
	/** Header bytes used when deploying - tests can override to use a different AT version */
	public byte[] headerBytes = TestUtils.HEADER_BYTES;

	@BeforeClass
	public static void beforeClass() {
		Security.insertProviderAt(new BouncyCastleProvider(), 0);
	}

	@Before
	public void beforeTest() {
		loggerFactory = new TestLoggerFactory();
		api = new TestAPI();
		codeByteBuffer = ByteBuffer.allocate(TestUtils.NUM_CODE_PAGES * MachineState.OPCODE_SIZE);
		dataByteBuffer = ByteBuffer.allocate(TestUtils.NUM_DATA_PAGES * MachineState.VALUE_SIZE);
		stateByteBuffer = null;
		packedState = null;
	}

	@After
	public void afterTest() {
		packedState = null;
		stateByteBuffer = null;
		codeByteBuffer = null;
		dataByteBuffer = null;
		api = null;
		loggerFactory = null;
	}

	public void execute(boolean onceOnly) {
		if (packedState == null) {
			// First time
			System.out.println("First execution - deploying...");
			codeBytes = codeByteBuffer.array();
			byte[] dataBytes = dataByteBuffer.array();

			state = new MachineState(api, loggerFactory, headerBytes, codeBytes, dataBytes);
			packedState = state.toBytes();
		}

		do {
			execute_once();
		} while (!onceOnly && !state.isFinished());

		unwrapState(state);
	}

	public void execute_once() {
		state = MachineState.fromBytes(api, loggerFactory, packedState, codeBytes);

		System.out.println("Starting execution round!");
		System.out.println("Current block height: " + api.getCurrentBlockHeight());
		System.out.println("Previous balance: " + TestAPI.prettyAmount(state.getPreviousBalance()));
		System.out.println("Current balance: " + TestAPI.prettyAmount(api.getCurrentBalance(state)));

		// Actual execution
		if (api.willExecute(state, api.getCurrentBlockHeight())) {
			// Actual execution
			api.preExecute(state);
			state.execute();
			packedState = state.toBytes();

			System.out.println("After execution round:");
			System.out.println("Steps: " + state.getSteps());
			System.out.println(String.format("Program Counter: 0x%04x", state.getProgramCounter()));
			System.out.println(String.format("Stop Address: 0x%04x", state.getOnStopAddress()));
			System.out.println("Error Address: " + (state.getOnErrorAddress() == null ? "not set" : String.format("0x%04x", state.getOnErrorAddress())));

			if (state.isSleeping())
				System.out.println("Sleeping until current block height (" + state.getCurrentBlockHeight() + ") reaches " + state.getSleepUntilHeight());
			else
				System.out.println("Sleeping: " + state.isSleeping());

			System.out.println("Stopped: " + state.isStopped());
			System.out.println("Finished: " + state.isFinished());

			if (state.hadFatalError())
				System.out.println("Finished due to fatal error!");

			System.out.println("Frozen: " + state.isFrozen());

			long newBalance = state.getCurrentBalance();
			System.out.println("New balance: " + TestAPI.prettyAmount(newBalance));

			// Update AT balance due to execution costs, etc.
			api.setCurrentBalance(newBalance);
		} else {
			System.out.println("Skipped execution round");
		}

		// Add block, possibly containing AT-created transactions, to chain to at least provide block hashes
		api.addCurrentBlockToChain();

		// Bump block height
		api.bumpCurrentBlockHeight();

		System.out.println("Execution round finished\n");
	}

	public byte[] unwrapState(MachineState state) {
		// Ready for diagnosis
		byte[] stateBytes = state.toBytes();

		// We know how the state will be serialized so we can extract values
		// header + data(size * 8) + callStack length(4) + callStack + userStack length(4) + userStack

		stateByteBuffer = ByteBuffer.wrap(stateBytes);
		callStackSize = stateByteBuffer.getInt(CALL_STACK_OFFSET);
		userStackOffset = CALL_STACK_OFFSET + 4 + callStackSize;
		userStackSize = stateByteBuffer.getInt(userStackOffset);

		return stateBytes;
	}

	public long getData(int address) {
		int index = DATA_OFFSET + address * MachineState.VALUE_SIZE;
		return stateByteBuffer.getLong(index);
	}

	public void getDataBytes(int address, byte[] dest) {
		int index = DATA_OFFSET + address * MachineState.VALUE_SIZE;
		stateByteBuffer.slice().position(index).get(dest);
	}

	public int getCallStackPosition() {
		return TestUtils.NUM_CALL_STACK_PAGES * MachineState.ADDRESS_SIZE - callStackSize;
	}

	public int getCallStackEntry(int address) {
		int index = CALL_STACK_OFFSET + 4 + address - TestUtils.NUM_CALL_STACK_PAGES * MachineState.ADDRESS_SIZE + callStackSize;
		return stateByteBuffer.getInt(index);
	}

	public int getUserStackPosition() {
		return TestUtils.NUM_USER_STACK_PAGES * MachineState.VALUE_SIZE - userStackSize;
	}

	public long getUserStackEntry(int address) {
		int index = userStackOffset + 4 + address - TestUtils.NUM_USER_STACK_PAGES * MachineState.VALUE_SIZE + userStackSize;
		return stateByteBuffer.getLong(index);
	}

}
