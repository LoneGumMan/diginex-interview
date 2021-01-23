package alick.diginex.matchingengine.message;

/**
 * FIX-like message to denote a rejection for amend / cancel reject
 */
public class OrderCancelReject implements ResponseMessage {
	@Override
	public MessageType getMessageType() {
		return MessageType.ORDER_CANCEL_REJECT;
	}

	private final String clOrdId;
	private final String origClOrdId;
	private final String rejectReason;

	public OrderCancelReject(final String clOrdId, final String origClOrdId, final String rejectReason) {
		this.clOrdId = clOrdId;
		this.origClOrdId = origClOrdId;
		this.rejectReason = rejectReason;
	}

	public String getClOrdId() {
		return clOrdId;
	}

	public String getOrigClOrdId() {
		return origClOrdId;
	}

	public String getRejectReason() {
		return rejectReason;
	}
}
