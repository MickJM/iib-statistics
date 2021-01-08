package app.com.mq.metrics.mqmetrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

//@ComponentScan("app.com.mq.metrics.mqmetrics.MQConnection")
//@ComponentScan("app.com.mq.pcf.channel.pcfChannel")
//@ComponentScan("app.com.mq.metrics.mqmetrics.MQMetricsApplicationTests")
//@ComponentScan("app.com.mq.json.controller.JSONController")
// ,"app.com.mq.json.controller"
@ComponentScan(basePackages = { "app.com.mq.metrics.mqmetrics"} )
@ComponentScan("app.com.mq.pcf.channel.pcfListener")
@SpringBootApplication
@EnableScheduling
public class MQMetricsApplication {

	public static void main(String[] args) {
		SpringApplication sa = new SpringApplication(MQMetricsApplication.class);
		sa.run(args);
		
	}
	
	
}
