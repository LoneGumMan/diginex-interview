package alick.diginex.matchingengine.message;


public interface ResponseMessage {

	enum MessageType {
		BUSINESS_MESSAGE_REJECT,
		REJECT,
		ORDER_CANCEL_REJECT,
		EXECUTION_REPORT
	}

	MessageType getMessageType();
}