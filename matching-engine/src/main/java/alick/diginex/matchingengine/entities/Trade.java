package alick.diginex.matchingengine.entities;

import java.time.Instant;

public class Trade {
	private final double execQty;
	private final double tradePx;
	private final Instant transactTime;

	public Trade(final double execQty, final double tradePx, final Instant transactTime) {
		this.execQty = execQty;
		this.tradePx = tradePx;
		this.transactTime = transactTime;
	}

	public double getExecQty() {
		return execQty;
	}

	public double getTradePx() {
		return tradePx;
	}

	public Instant getTransactTime() {
		return transactTime;
	}
}
