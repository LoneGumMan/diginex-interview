package alick.diginex.util;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * A <em>roughly</em> date based internal ID generator so the engine can run.. potentially forever.
 *
 * There is a need to generate unique id, instead of just starting from 0 upon restart, but this ends up costing
 * ~0.1 ms on every call, and is a point of contention. Replace this with something better.
 */
public final class IdGenerator {
	private static final int DEFAULT_UPDATE_INTERVAL = (int) TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS);
	private LocalDate referenceDate;
	private long leadingBitMask;
	private long orderCount;
	private long lastCall;
	private final int updateIntervalInMs;

	private static long refDateToLeadingBits(final LocalDate refDate) {
		// year + day-of-year takes 22-bit to represent 2020 ~ 3099/12/31
		// shift = 64-bit int  - 1 bit sign - 22 bits for date = 41 bits
		// has room for 4 trillion order IDs per day...good enough?
		// we are really limited by the lastCall mill-since-epoch anyway
		final long dateNum = refDate.getYear() * 1000L + refDate.getDayOfYear();
		return dateNum << (64 - 1 - 22);
	}

	public IdGenerator() {
		this(LocalDate.now(), DEFAULT_UPDATE_INTERVAL);
	}

	//package accessible for testing
	IdGenerator(final LocalDate referenceDate, final int updateIntervalInMs) {
		this.lastCall = System.currentTimeMillis();
		this.updateIntervalInMs = updateIntervalInMs;
		setReferenceDate(referenceDate);
	}

	private void setReferenceDate(final LocalDate refDate) {
		this.referenceDate = refDate;
		this.leadingBitMask = refDateToLeadingBits(refDate);
		this.orderCount = 0;
	}

	public long getNextId() {
		final long now = System.currentTimeMillis();
		// do the reset only every 10 seconds? otherwise this can get expensive
		synchronized(this) {
			if ((now - lastCall) > updateIntervalInMs) {
				final LocalDate currentDate = LocalDate.now();
				if (!currentDate.isEqual(referenceDate)) {
					setReferenceDate(currentDate);
				}
			}
			lastCall = now;
			return leadingBitMask | (++orderCount);
		}
	}
}