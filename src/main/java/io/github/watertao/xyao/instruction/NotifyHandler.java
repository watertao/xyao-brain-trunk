package io.github.watertao.xyao.instruction;

import io.github.watertao.xyao.infras.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.impl.triggers.SimpleTriggerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.quartz.SimpleTrigger;
import org.springframework.util.StringUtils;
import sun.security.provider.MD5;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.Set;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.JobKey.jobKey;
import static org.quartz.TriggerBuilder.newTrigger;


@Service("notify")
@Instruction(
  syntax = "notify [options...] <message> ",
  description = "设置提醒",
  masterOnly = true, // 为了避免群内其他用户肆意注册大量提醒任务导致 brain module 资源耗尽，暂定 master only
  msgEnv = MessageEnvironmentEnum.BOTH
)
public class NotifyHandler extends AbstractInstructionHandler {

  private static final Logger logger = LoggerFactory.getLogger(NotifyHandler.class);

  private static final String GROUP_NAME = "xyao";

  private Scheduler scheduler;

  private ThreadLocal<SimpleDateFormat> sdfThd = new ThreadLocal<>();

  @Autowired
  private XyaoChannelProxy channelProxy;

  public NotifyHandler() throws SchedulerException {
    StdSchedulerFactory sf = new StdSchedulerFactory();
    Properties properties = new Properties();
    properties.setProperty("org.quartz.scheduler.instanceName", "xyao");
    properties.setProperty("org.quartz.threadPool.threadCount", "5");
    properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");

    sf.initialize(properties);
    scheduler = sf.getScheduler();

    scheduler.start();
  }

  @Override
  protected Options defineOptions() {

    Options options = new Options();
    options.addOption("d", "delay", true, "设置延迟提醒，单位（分钟）。-d 30 代表30分钟后提醒我");
    options.addOption("t", "datetime", true, "按时间设置提醒，格式 \"yyyy-MM-dd HH:mm\"");
    options.addOption("c", "crontab", true, "按 crontab 提醒。\"0 0 9 ? * FRI\" 代表每周五9点");
    options.addOption("l", "list", false, "列出当前有效的定时提醒任务");
    options.addOption("r", "remove", true, "根据 ID 删除指定的定时提醒任务");

    return options;

  }

  @Override
  protected void handle(XyaoInstruction instruction, CommandLine command) {

    if (command.hasOption("l")) {
      listJobs(instruction);
    } else if (command.hasOption("r")) {
      removeJob(instruction, command);
    } else if (command.hasOption("d")) {
      makeDelayNotify(instruction, command);
    } else if (command.hasOption("t")) {
      makeDatetimeNotify(instruction, command);
    } else if (command.hasOption("c")) {
      makeCrontabNotify(instruction, command);
    } else {
      throw new IllegalArgumentException("缺少选项");
    }


  }

  private void listJobs(XyaoInstruction instruction) {
    // scheduler

    try {
      Set<JobKey> jobKeySet = scheduler.getJobKeys(GroupMatcher.groupEquals(GROUP_NAME));
      StringBuilder sb = new StringBuilder();
      for (JobKey jobKey : jobKeySet) {
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        Trigger trigger = scheduler.getTriggersOfJob(jobKey).get(0);
        sb.append(jobDetail.getKey().getName()).append(" - ")
          .append("[ " + retrieveSdf().format(trigger.getNextFireTime()) + " ] ")
          .append(jobDetail.getJobDataMap().get("message"))
          .append("\n");
      }

      if (jobKeySet.size() == 0) {
        sb.append("当前无提醒");
      }

      XyaoMessage xyaoMessage = makeResponseMessage(instruction);
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity(sb.toString()));
      channelProxy.publish(xyaoMessage);

    } catch (SchedulerException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private void removeJob(XyaoInstruction instruction, CommandLine command) {

    try {
      boolean result = scheduler.deleteJob(jobKey(command.getOptionValue("r"), GROUP_NAME));
      XyaoMessage xyaoMessage = makeResponseMessage(instruction);
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity(result ? "已删除提醒" : "未找到提醒"));
      channelProxy.publish(xyaoMessage);
    } catch (SchedulerException e) {
      e.printStackTrace();
    }

  }

  private void makeDelayNotify(XyaoInstruction instruction, CommandLine command) {

    Integer delay = null;
    try {
      delay = Integer.valueOf(command.getOptionValue("d"));
    } catch (Exception e) {
      throw new IllegalArgumentException("非法选项值 [ " + command.getOptionValue("d") + " ]");
    }

    if (delay < 0) {
      throw new IllegalArgumentException("选项[ delay ]的值不可为负数");
    }

    if (command.getArgList().size() < 1) {
      throw new IllegalArgumentException("消息文本缺失");
    }

    JobDetail jobDetail = makeJobDetail(instruction, command);

    Trigger trigger = newTrigger()
      .withIdentity(makeId(), GROUP_NAME)
      .startAt(new Date(System.currentTimeMillis() + (60 * 1000 * delay))) // some Date
      .forJob(jobDetail) // identify job with name, group strings
      .build();

    try {
      scheduler.scheduleJob(jobDetail, trigger);
      XyaoMessage xyaoMessage = makeResponseMessage(instruction);
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity("好的，我会提醒的"));
      channelProxy.publish(xyaoMessage);
    } catch (SchedulerException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }

  }

  private void makeDatetimeNotify(XyaoInstruction instruction, CommandLine command) {
    Date notifyTime = null;

    try {
      notifyTime = retrieveSdf().parse(command.getOptionValue("t"));
    } catch (ParseException e) {
      throw new IllegalArgumentException("非法选项值 [ " + command.getOptionValue("d") + " ]");
    }

    if (notifyTime.compareTo(new Date()) <= 0) {
      throw new IllegalArgumentException("日期必须晚于当前时间");
    }

    if (command.getArgList().size() < 1) {
      throw new IllegalArgumentException("消息文本缺失");
    }

    JobDetail jobDetail = makeJobDetail(instruction, command);

    Trigger trigger = newTrigger()
      .withIdentity(makeId(), GROUP_NAME)
      .startAt(notifyTime) // some Date
      .forJob(jobDetail) // identify job with name, group strings
      .build();

    try {
      scheduler.scheduleJob(jobDetail, trigger);
      XyaoMessage xyaoMessage = makeResponseMessage(instruction);
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity("好的，我会提醒的"));
      channelProxy.publish(xyaoMessage);
    } catch (SchedulerException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private void makeCrontabNotify(XyaoInstruction instruction, CommandLine command) {

    if (command.getArgList().size() < 1) {
      throw new IllegalArgumentException("消息文本缺失");
    }

    JobDetail jobDetail = makeJobDetail(instruction, command);

    Trigger trigger = newTrigger()
      .withIdentity(makeId(), GROUP_NAME)
      .withSchedule(CronScheduleBuilder.cronSchedule(command.getOptionValue("c")))
      .forJob(jobDetail) // identify job with name, group strings
      .build();

    try {
      scheduler.scheduleJob(jobDetail, trigger);
      XyaoMessage xyaoMessage = makeResponseMessage(instruction);
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity("好的，我会提醒的"));
      channelProxy.publish(xyaoMessage);
    } catch (SchedulerException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private String makeId() {
    return DigestUtils.md5DigestAsHex(String.valueOf(System.currentTimeMillis()).getBytes()).substring(0, 6);
  }


  private JobDetail makeJobDetail(XyaoInstruction instruction, CommandLine commandLine) {

    String message = null;
    if (commandLine.getArgList().size() > 1) {
      message = String.join(" ", commandLine.getArgList());
    } else {
      message = commandLine.getArgList().get(0);
    }

    JobDetail jobDetail = newJob(NotifyJob.class)
      .withIdentity(makeId(), GROUP_NAME)
      .build();
    jobDetail.getJobDataMap().put("instruction", instruction);
    jobDetail.getJobDataMap().put("message", message);
    jobDetail.getJobDataMap().put("channelProxy", channelProxy);

    return jobDetail;

  }

  private SimpleDateFormat retrieveSdf() {
    if (sdfThd.get() == null) {
      sdfThd.set(new SimpleDateFormat("yyyy-MM-dd HH:mm"));
    }
    return sdfThd.get();
  }

  public static class NotifyJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
      XyaoInstruction instruction = (XyaoInstruction) context.getJobDetail().getJobDataMap().get("instruction");
      String message = (String) context.getJobDetail().getJobDataMap().get("message");
      XyaoChannelProxy channelProxy = (XyaoChannelProxy) context.getJobDetail().getJobDataMap().get("channelProxy");

      XyaoMessage xyaoMessage = new XyaoMessage();
      xyaoMessage.getTo().add(new XyaoMessage.Contact(){{
        setId(instruction.getFrom().getId());
        setName(instruction.getFrom().getName());
        setIsMention(true);
      }});
      xyaoMessage.setRoom(instruction.getRoom());
      xyaoMessage.getEntities().add(new XyaoMessage.StringEntity(message));

      channelProxy.publish(xyaoMessage);

    }
  }

}
