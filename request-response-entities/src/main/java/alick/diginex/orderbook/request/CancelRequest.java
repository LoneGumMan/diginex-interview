package alick.diginex.orderbook.request;

public class CancelRequest extends Request {
	@lombok.Builder(builderClassName = "Builder")
	private CancelRequest(final long orderId) {
		super(orderId);
	}
}
