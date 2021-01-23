package alick.diginex.matchingengine.message;

/**
 * FIX-like message to denote a rejection of an invalid request
 */
public class BusinessMessageReject implements ResponseMessage {
	@Override
	public MessageType getMessageType() {
		return MessageType.BUSINESS_MESSAGE_REJECT;
	}
}
