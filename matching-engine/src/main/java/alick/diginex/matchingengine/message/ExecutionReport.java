package alick.diginex.matchingengine.message;

import alick.diginex.entities.OrderType;
import alick.diginex.entities.Side;
import alick.diginex.matchingengine.entities.OrderStatus;
import lombok.Getter;
import lombok.NonNull;

/**
 * FIX-like execution report, for acknowledgement, fills, etc.
 */
@Getter
@lombok.Builder(builderClassName = "Builder")
public class ExecutionReport implements ResponseMessage {
	@NonNull
	private final String clOrdId;
	@lombok.Builder.Default
	private final String origClOrdId = null;
	private final long orderId;
	@NonNull
	private final OrderStatus orderStatus;

	@NonNull
	private final Side side;
	@NonNull
	private final OrderType orderType;

	private final double orderQty;
	private final double price;
	private final double cumQty;
	private final double leavesQty;
	private final double avgPx;

	@lombok.Builder.Default
	private final Double lastQty = null;
	@lombok.Builder.Default
	private final Double lastPx = null;

	@lombok.Builder.Default
	private final String rejectReason = null;

	@Override
	public MessageType getMessageType() {
		return MessageType.EXECUTION_REPORT;
	}
}
