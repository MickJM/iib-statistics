package app.com.mq.metrics.entities;

public class AccountingMetrics {

	private String queueName;
	private int[] values;
	
	public String getQueueName() {
		return queueName;
	}
	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}
	public int[] getValues() {
		return values;
	}
	public void setValues(int[] values) {
		this.values = values;
	}
	
	
}
