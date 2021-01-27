package alick.diginex.matchingengine.entities;

import lombok.Getter;
import lombok.NonNull;

import java.time.Instant;

@lombok.Builder(builderClassName = "Builder")
@Getter
public class Trade {
	private final double execQty;
	private final double tradePx;
	@NonNull
	private final Instant transactTime;
}
