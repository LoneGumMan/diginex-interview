package alick.diginex.matchingengine.message;

import lombok.Getter;

/**
 * FIX-like message to denote a rejection for amend / cancel reject
 */
@Getter
@lombok.Builder(builderClassName = "Builder")
public class OrderCancelReject implements ResponseMessage {
	@Override
	public MessageType getMessageType() {
		return MessageType.ORDER_CANCEL_REJECT;
	}

	private final String clOrdId;
	private final String origClOrdId;
	private final String rejectReason;
}
