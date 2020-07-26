package io.github.watertao.xyao.instruction;

import io.github.watertao.xyao.infras.AbstractInstructionHandler;
import io.github.watertao.xyao.infras.Instruction;
import io.github.watertao.xyao.infras.MessageEnvironmentEnum;
import io.github.watertao.xyao.infras.XyaoInstruction;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service("notify")
@Instruction(
  syntax = "notify [options...] <message> ",
  description = "设置提醒",
  masterOnly = false,
  msgEnv = MessageEnvironmentEnum.BOTH
)
public class NotifyHandler extends AbstractInstructionHandler {

  private static final Logger logger = LoggerFactory.getLogger(NotifyHandler.class);

  @Override
  protected Options defineOptions() {

    Options options = new Options();
    options.addOption("d", "delay", true, "设置延迟提醒，单位（分钟）");
    options.addOption("t", "time", true, "按时间设置提醒，格式 HH:mm");
    options.addOption("w", "weekdays", true, "按周循环提醒，需结合 -t 选项使用。如 -w 1,3,5 代表每周一三五");

    return options;

  }

  @Override
  protected void handle(XyaoInstruction instruction, CommandLine command) {

    if (command.hasOption("u")) {
      unbind(instruction);
    } else if (command.hasOption("p") && command.getOptionValue("p") != null) {
      bind(instruction, command.getOptionValue("p"));
    }

  }

  private void unbind(XyaoInstruction instruction) {

  }

  private void bind(XyaoInstruction instruction, String projectId) {

  }


}
