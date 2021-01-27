package alick.diginex.orderbook.response;

import lombok.Getter;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;

@Getter
public class ErrorResponse extends Response {
	private final String errorMsg;

	@lombok.Builder(builderClassName = "Builder")
	private ErrorResponse(final long orderId,  @NonNull final Level2Summary bidSummary,  @NonNull final Level2Summary askSummary, @NonNull final String errorMsg,  final List<Execution> executions) {
		super(orderId, bidSummary, askSummary, executions);
		this.errorMsg = Objects.requireNonNull(errorMsg, "error message");
	}
}
