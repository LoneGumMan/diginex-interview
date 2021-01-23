package alick.diginex.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class IdGeneratorTest {
	@Test
	public void firstID() {
		final LocalDate localDate = LocalDate.of(2000, 1, 1);
		final IdGenerator gen = new IdGenerator(localDate, 100);

		final long orderId = gen.getNextId();

		final long dateNum = orderId >> 41;

		final int year = (int) (dateNum / 1000);
		final int dayOfYear = (int) (dateNum % 1000);
		final long orderIdPart = 0x01ff_ffff_ffffL & orderId;

		assertThat("date from ID", LocalDate.ofYearDay(year, dayOfYear), is(localDate));
		assertThat("order ID part", orderIdPart, is(1L));// first ID
	}

	@Test
	public void referenceDateUpdatesAfterGivenInterval() throws InterruptedException {
		final LocalDate startDate = LocalDate.of(2000, 1, 1);
		final IdGenerator gen = new IdGenerator(startDate, 100);

		final long firstId = gen.getNextId();
		Thread.sleep(101);//make sure the date swaps out, 100 mill for interval, sleeping 101 millisecond is plenty

		final LocalDate today = LocalDate.now();
		for (int i = 0; i < 1000000; ++i)
			gen.getNextId();

		final long laterId = gen.getNextId();

		assertThat("later ID is numerically greater than earlier ID", laterId, greaterThan(firstId));

		final long dateNum = laterId >> 41;

		final int year = (int) (dateNum / 1000);
		final int dayOfYear = (int) (dateNum % 1000);
		final long orderIdPart = 0x01ff_ffff_ffffL & laterId;

		assertThat("date from ID", LocalDate.ofYearDay(year, dayOfYear), is(today));
	}
}
