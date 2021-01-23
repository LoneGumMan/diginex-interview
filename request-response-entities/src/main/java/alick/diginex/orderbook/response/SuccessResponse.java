package alick.diginex.orderbook.response;

import java.util.List;

public class SuccessResponse extends Response {
	public SuccessResponse(final Long orderId, final Level2Summary bidSummary, final Level2Summary askSummary, final List<Execution> executions) {
		super(orderId, bidSummary, askSummary, executions);
	}
}