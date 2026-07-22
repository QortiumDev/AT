package org.ciyam.at;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * API for CIYAM AT "Function Codes" for blockchain-specific interactions.
 * <p>
 * For more information, see the specification document at:<br>
 * <a href="http://ciyam.org/at/at_api.html">Automated Transactions API Specification</a>
 * <p>
 * Note that "timestamp" does not mean a real timestamp but instead is an artificial timestamp that includes two parts. The first part is a block height (32
 * bits) with the second part being the number of the transaction if applicable (also 32 bits and zero if not applicable).
 *
 */
public abstract class API {

	/** Suggested transaction types to be used by the AT sub-system */
	public enum ATTransactionType {
		PAYMENT(0),
		MESSAGE(1);

		public final long value;

		private static final Map<Long, ATTransactionType> map = stream(ATTransactionType.values()).collect(toMap(type -> type.value, type -> type));

		ATTransactionType(long value) {
			this.value = value;
		}

		public static ATTransactionType valueOf(long value) {
			return map.get(value);
		}
	}

	/** Returns maximum number of permitted steps per execution round */
	public abstract int getMaxStepsPerRound();

	/** Returns fee for executing opcode in terms of execution "steps" */
	public abstract int getOpCodeSteps(OpCode opcode);

	/**
	 * Returns fee for executing an external-function opcode in terms of execution "steps".
	 * <p>
	 * This overload is called before the machine enforces its step and balance budgets, and before
	 * the external function executes. Platforms that need function-specific pricing can inspect the
	 * raw function code and current machine state here. The default delegates to the opcode-only
	 * method so existing API implementations and pricing remain unchanged.
	 *
	 * @param opcode external-function opcode about to execute
	 * @param rawFunctionCode raw function code encoded after the opcode
	 * @param state current machine state before the function is charged or executed
	 * @return number of execution steps to charge for the function call
	 */
	public int getOpCodeSteps(OpCode opcode, short rawFunctionCode, MachineState state) {
		return this.getOpCodeSteps(opcode);
	}

	/** Returns fee per execution "step" */
	public abstract long getFeePerStep();

	/** Returns current blockchain's height */
	public abstract int getCurrentBlockHeight();

	/** Returns block height where AT was created */
	public abstract int getATCreationBlockHeight(MachineState state);

	/** Returns previous block's height */
	public int getPreviousBlockHeight() {
		return getCurrentBlockHeight() - 1;
	}

	/** Put previous block's signature/hash into A */
	public abstract void putPreviousBlockHashIntoA(MachineState state);

	/** Put signature/hash of next transaction sent to AT after timestamp in A, or zero A if no more transactions */
	public abstract void putTransactionAfterTimestampIntoA(Timestamp timestamp, MachineState state);

	/** Return type from transaction in A, or 0xffffffffffffffff if A not valid transaction */
	public abstract long getTypeFromTransactionInA(MachineState state);

	/** Return amount from transaction in A, after transaction fees have been deducted, or 0xffffffffffffffff if A not valid transaction */
	public abstract long getAmountFromTransactionInA(MachineState state);

	/** Return timestamp from transaction in A, or 0xffffffffffffffff if A not valid transaction */
	public abstract long getTimestampFromTransactionInA(MachineState state);

	/**
	 * Generate pseudo-random number using transaction in A.
	 * <p>
	 * AT should sleep so it can use next block as source of entropy.
	 * <p>
	 * Set <code>state.isSleeping = true</code> before exit on first call.<br>
	 * <code>state.steps</code> will be zero on second call after wake-up.
	 * <p>
	 * Returns 0xffffffffffffffff if A not valid transaction.
	 */
	public abstract long generateRandomUsingTransactionInA(MachineState state);

	/** Put 'message' from transaction in A into B, or zero B if not a message transaction */
	public abstract void putMessageFromTransactionInAIntoB(MachineState state);

	/** Put sender/creator address from transaction in A into B */
	public abstract void putAddressFromTransactionInAIntoB(MachineState state);

	/** Put AT's creator's address into B */
	public abstract void putCreatorAddressIntoB(MachineState state);

	/** Return AT's current balance */
	public abstract long getCurrentBalance(MachineState state);

	/**
	 * Pay up to {@code amount} (fee inclusive) to the address in B, returning the amount actually paid.
	 * <p>
	 * An implementation may pay less than requested — for example clamping to a genuinely spendable
	 * balance or rounding to a whole quantity of an indivisible asset — or pay nothing at all (returning
	 * 0), e.g. for a non-positive request. The caller debits the machine balance by the returned amount,
	 * never by the requested amount, so the machine balance always reflects what was really emitted.
	 */
	public abstract long payAmountToB(long amount, MachineState state);

	/** Send 'message' in A to address in B */
	public abstract void messageAToB(MachineState state);

	/**
	 * Returns <code>minutes</code> of blocks added to 'timestamp'
	 * <p>
	 * <code>minutes</code> is converted to rough number of blocks and added to 'timestamp' to create return value.
	 */
	public abstract long addMinutesToTimestamp(Timestamp timestamp, long minutes, MachineState state);

	/**
	 * AT has finished. Return remaining funds to creator.
	 * 
	 * @param amount
	 *            - final balance to be returned to creator
	 * @param state AT machine state
	 */
	public abstract void onFinished(long amount, MachineState state);

	/** AT has encountered fatal error */
	public abstract void onFatalError(MachineState state, ExecutionException e);

	/** Pre-execute checking of param requirements for platform-specific functions */
	public abstract void platformSpecificPreExecuteCheck(int paramCount, boolean returnValueExpected, MachineState state, short rawFunctionCode)
			throws IllegalFunctionCodeException;

	/**
	 * Platform-specific function execution after checking correct calling OpCode
	 * 
	 * @throws ExecutionException
	 */
	public abstract void platformSpecificPostCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException;

	/** Convenience method to allow subclasses to access package-scoped MachineState.setIsSleeping */
	protected void setIsSleeping(MachineState state, boolean isSleeping) {
		state.setIsSleeping(isSleeping);
	}

	/** Convenience method to allow subclasses to test package-scoped MachineState.isFirstOpCodeAfterSleeping */
	protected boolean isFirstOpCodeAfterSleeping(MachineState state) {
		return state.isFirstOpCodeAfterSleeping();
	}

	/** Convenience method to allow subclasses to access MachineState.rewindCodePosition */
	protected void rewindCodePosition(MachineState state, int offset) {
		state.rewindCodePosition(offset);
	}

	protected void setSleepUntilHeight(MachineState state, Integer height) {
		state.setSleepUntilHeight(height);
	}

	/* Convenience methods to allow subclasses to access package-scoped a1-a4, b1-b4 variables */

	protected long getA1(MachineState state) {
		return state.a1;
	}

	protected long getA2(MachineState state) {
		return state.a2;
	}

	protected long getA3(MachineState state) {
		return state.a3;
	}

	protected long getA4(MachineState state) {
		return state.a4;
	}

	protected byte[] getA(MachineState state) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 8);

		byteBuffer.putLong(state.a1);
		byteBuffer.putLong(state.a2);
		byteBuffer.putLong(state.a3);
		byteBuffer.putLong(state.a4);

		return byteBuffer.array();
	}

	protected long getB1(MachineState state) {
		return state.b1;
	}

	protected long getB2(MachineState state) {
		return state.b2;
	}

	protected long getB3(MachineState state) {
		return state.b3;
	}

	protected long getB4(MachineState state) {
		return state.b4;
	}

	protected byte[] getB(MachineState state) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(4 * 8);

		byteBuffer.putLong(state.b1);
		byteBuffer.putLong(state.b2);
		byteBuffer.putLong(state.b3);
		byteBuffer.putLong(state.b4);

		return byteBuffer.array();
	}

	protected void zeroA(MachineState state) {
		state.a1 = 0L;
		state.a2 = 0L;
		state.a3 = 0L;
		state.a4 = 0L;
	}

	protected void zeroB(MachineState state) {
		state.b1 = 0L;
		state.b2 = 0L;
		state.b3 = 0L;
		state.b4 = 0L;
	}

	protected void setAToMaxValue(MachineState state) {
		state.a1 = 0xffffffffffffffffL;
		state.a2 = 0xffffffffffffffffL;
		state.a3 = 0xffffffffffffffffL;
		state.a4 = 0xffffffffffffffffL;
	}

	protected void setA1(MachineState state, long value) {
		state.a1 = value;
	}

	protected void setA2(MachineState state, long value) {
		state.a2 = value;
	}

	protected void setA3(MachineState state, long value) {
		state.a3 = value;
	}

	protected void setA4(MachineState state, long value) {
		state.a4 = value;
	}

	protected void setA(MachineState state, byte[] bytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		state.a1 = byteBuffer.getLong();
		state.a2 = byteBuffer.getLong();
		state.a3 = byteBuffer.getLong();
		state.a4 = byteBuffer.getLong();
	}

	protected void setB1(MachineState state, long value) {
		state.b1 = value;
	}

	protected void setB2(MachineState state, long value) {
		state.b2 = value;
	}

	protected void setB3(MachineState state, long value) {
		state.b3 = value;
	}

	protected void setB4(MachineState state, long value) {
		state.b4 = value;
	}

	protected void setB(MachineState state, byte[] bytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);

		state.b1 = byteBuffer.getLong();
		state.b2 = byteBuffer.getLong();
		state.b3 = byteBuffer.getLong();
		state.b4 = byteBuffer.getLong();
	}

}
