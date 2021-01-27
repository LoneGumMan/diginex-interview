package alick.diginex.orderbook;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;

import java.util.List;

/**
 * The result of matching execution.
 * </p>
 * Contains the total matched quantity, and the list of orders matched against with the respective matched quantities
 */
@Getter
@ToString(of={"totalMatchedQuantity", "matchedOrders", "doneOrderIds"})
@Builder
class MatchResult {
	private final double totalMatchedQuantity;
	@NonNull
	private final List<OrderBucket.MatchedOrder> matchedOrders;
	@NonNull
	private final LongArrayList doneOrderIds;
}
