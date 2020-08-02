# xyao-brain-trunk
[![Powered by Wechaty](https://img.shields.io/badge/Powered%20By-Wechaty-green.svg)](https://github.com/chatie/wechaty)
[![Wechaty开源激励计划](https://img.shields.io/badge/Wechaty-开源激励计划-green.svg)](https://github.com/juzibot/Welcome/wiki/Everything-about-Wechaty)

**xyao-brain-trunk** is a brain module of [wechaty-plugin-xyao](https://github.com/watertao/wechaty-plugin-xyao), it provides common features, such as setting up a notification, connecting rooms, playing dice , etc...

# brain identifier
`x`

> note：A prefix should be appended before instruction, e.g.  `x:notify`

# Supported Instructions

### _notify_
`room & whisper` `masterOnly`

设置提醒。
私聊发送的指令，将会在私聊窗口提醒，而群组内发送的指令在群组内 @ 发送者提醒。

``` bash
# 30 分钟后提醒我喝水
notify -d 30 hey，你该喝水了！

# 到 10：30 提醒我参加会议
notify -t "2020-08-03 09:30" 该去开会了！

# 每周五16：00 提醒写周报
notify -c "0 0 16 ? * FRI" 周报周报

# 列出当前有效的提醒
notify -l

# 删除 id 为 a35be3 的提醒
notify -r a35be3

```


### _dice_
`room` `everyone`

掷骰子。
返回1到6点。成绩默认保留30分钟。

``` bash
# 掷骰子
dice

# 按大小和投掷时间顺序列出群组内各掷骰者的成绩
dice -l

```


# Usage

1. make sure you have already deployed a wechaty bot with plugin [wechaty-plugin-xyao](https://github.com/watertao/wechaty-plugin-xyao)
1. clone this project
1. modify `application.properties`, config redis related parameters
1. use `mvn package` to build a executable jar
1. run the jar
