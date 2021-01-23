package alick.diginex.orderbook.response;

import java.util.List;
import java.util.Objects;

public class ErrorResponse extends Response {
	private final String errorMsg;

	public ErrorResponse(final Long orderId, final Level2Summary bidSummary, final Level2Summary askSummary, final String errorMsg, final List<Execution> executions) {
		super(orderId, bidSummary, askSummary, executions);
		this.errorMsg = Objects.requireNonNull(errorMsg, "error message");
	}

	public String getErrorMsg() {
		return errorMsg;
	}
}
