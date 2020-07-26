package io.github.watertao.xyao.instruction;

import io.github.watertao.xyao.infras.*;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


@Service("dice")
@Instruction(
  syntax = "dice [options...]",
  description = "🎲掷骰子",
  masterOnly = false,
  msgEnv = MessageEnvironmentEnum.ROOM
)
public class DiceHandler extends AbstractInstructionHandler {

  private static final Logger logger = LoggerFactory.getLogger(DiceHandler.class);

  private static final String diceEmoji = "🎲";

  private static final Long expireMS = 30 * 60 * 1000l;

  private static final Map<Integer, String> points = new HashMap<Integer, String>(){{
    put(1, "1️⃣");
    put(2, "2️⃣");
    put(3, "3️⃣");
    put(4, "4️⃣");
    put(5, "5️⃣");
    put(6, "6️⃣");
  }};

  private Map<String, Map<String, Object[]>> scores = new HashMap<>();

  @Autowired
  private XyaoChannelProxy channelProxy;


  @Override
  protected Options defineOptions() {

    Options options = new Options();
    options.addOption("l", "list", false, "按点数大小和投掷时间排序列出成绩");

    return options;

  }

  @Override
  protected void handle(XyaoInstruction instruction, CommandLine command) {

    if (command.hasOption("l")) {
      list(instruction);
    } else {
      roll(instruction);
    }

  }

  private void list(XyaoInstruction instruction) {
    XyaoMessage xMessage = makeResponseMessage(instruction);
    Map<String, Object[]> roomScore = scores.get(instruction.getRoom().getId());
    if (roomScore == null) {
      xMessage.getEntities().add(new XyaoMessage.StringEntity("no scores"));
    } else {
      List<Map.Entry<String, Object[]>> scoreList = new LinkedList();
      roomScore.entrySet().forEach(entry -> {
        if (System.currentTimeMillis() - ((Date) (entry.getValue()[1])).getTime() < expireMS) {
          scoreList.add(entry);
        }
      });

      Collections.sort(scoreList, (Comparator<Map.Entry<String, Object[]>>) (t1, t2) -> {
        if ((Integer) t1.getValue()[0] > (Integer) t2.getValue()[0]) {
          return 1;
        } else if ((Integer) t1.getValue()[0] < (Integer) t2.getValue()[0]) {
          return 0;
        } else {
          return ((Date) t1.getValue()[1]).compareTo((Date)t2.getValue()[1]);
        }
      });

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < scoreList.size(); i++) {
        Map.Entry<String, Object[]> entry = scoreList.get(i);
        sb.append(i + 1).append(". ").append(points.get(entry.getValue()[0])).append("  ")
          .append(entry.getValue()[2]).append("\n");
      }

      xMessage.getEntities().add(new XyaoMessage.StringEntity(sb.toString()));
    }

    channelProxy.publish(xMessage);

  }

  private void roll(XyaoInstruction instruction) {

    Integer point = new Random().nextInt(6) + 1;
    recordScore(instruction, point);

    XyaoMessage xMessage = makeResponseMessage(instruction);
    xMessage.getEntities().add(new XyaoMessage.StringEntity(diceEmoji + " - " + points.get(point)));

    channelProxy.publish(xMessage);

  }

  private void recordScore(XyaoInstruction instruction, Integer point) {

    if (scores.get(instruction.getRoom().getId()) == null) {
      scores.put(instruction.getRoom().getId(), new HashMap<>());
    }
    scores.get(instruction.getRoom().getId()).put(instruction.getFrom().getId(), new Object[]{
      point, new Date(), instruction.getFrom().getName()
    });

  }


}
