import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.UnaryOperator;

public class Interview {
	static class MulticastQueue<T> {
		final ArrayBlockingQueue<T> queue = new ArrayBlockingQueue<>(10);
		final CopyOnWriteArrayList<UnaryOperator<T>> consumers = new CopyOnWriteArrayList<>();
		final Thread t = new Thread() {
			@Override
			public void run() {
				try {
					while (!Thread.currentThread().isInterrupted()) {
						final T item = queue.take();
						for (final UnaryOperator<T> consumer : consumers) {
							try {
								consumer.apply(item);
							}
							catch (Throwable t) {
								// log it
							}
						}
					}
				}
				catch (
						InterruptedException ie) {
					Thread.currentThread().interrupt();
				}
			}
		};

		public MulticastQueue() {
			this.t.start();
		}

		public void publish(T t) throws InterruptedException {
			queue.put(t);
		}

		public void subscribe(UnaryOperator<T> consumer) throws InterruptedException {
			this.consumers.add(consumer);
		}

		public void stopQueue() {
			this.t.interrupt();
		}

		@Override
		protected void finalize() throws Throwable {
			stopQueue();
		}
	}
}