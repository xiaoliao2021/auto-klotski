# auto-klotski
### 项目介绍

本项目是基于Android的自动玩木块华容道游戏app，做这个项目的初衷是我觉得木块华容道这个游戏挺有意思的，由于本人玩这个游戏相对较菜，所以想着能不能写个程序来自动玩这个游戏，于是就有了这个项目。

#### keyword

* Android
* Java
* Chaquopy
* python
* 广度优先遍历

### 1.方案选择

实现自动玩木块华容道的方案多种多样

其中最简单的方案如下：

```flow
st=>start: 开始
op1=>operation: adb获取截图
op2=>operation: pc计算滑动路径
op3=>operation: adb执行滑动
cond=>condition: 是否结束?
e=>end: 结束
st->op1->op2->op3->cond
cond(yes)->e
cond(no)->op1
```

##### 优点

* 操作简单

* 配置环境相对简单(python，adb)

##### 缺点

* 使用时必须要用到pc作为计算平台

由于此方案使用时需要全程连接pc，所以本次将不采用该方案

#### 方案B

在Autojs等自动化平台下实现自动化

```flow
st=>start: 开始
op1=>operation: Autojs获取截图
op2=>operation: Autojs计算滑动路径
op3=>operation: Autojs执行滑动
cond=>condition: 是否结束?
e=>end: 结束
st->op1->op2->op3->cond
cond(yes)->e
cond(no)->op1
```

##### 优点

* 不需要pc端

* 易配置环境

##### 缺点

* 自动化平台一般需要收费
* 自动化平台需要学习成本
* js脚本计算性能相对较低(相对于Java,C,C++等编译型语言来说)

由于本人比较穷，所以本次将不采用该方案

#### 我的方案

对于图像处理(图像处理在该项目中不是重点)部分，opencv-python实在是太香了，所以我想在此项目中使用opencv-python来实现图像处理部分，因此使用了Chaquopy来实现opencv-python运行所需要的python环境，对于计算部分，自然是选择Java来实现，虽然Jni调C++也能实现高性能(大可不必)。

```flow
st=>start: 开始
op1=>operation: Android获取截图
(MediaProjection)
op2=>operation: 获取滑块数据
(opencv-python)
op3=>operation: Java计算滑动路径
op4=>operation: Android执行滑动
(无障碍服务)
cond=>condition: 是否结束?
e=>end: 结束
st->op1->op2->op3->op4->cond
cond(yes)->e
cond(no)->op1
```

### 2.核心算法

[参考链接](https://blog.csdn.net/weixin_34196559/article/details/79356847)

#### 数据结构

一个木块用一个字节存储


| 8 | 7 | 6-4 | 3-1 |
| :----:| :----:| :----: |:----: |
| 方向位 | 长度位 | 行数 |列数 |
| 0:垂直1:水平 | 0:长度为二1:长度为三 | 行索引 |列索引 |

由于木块格子为6*6，因此可以用36位二进制来表示当前滑块的占用情况，此处使用long来存储

如：<img src=".\imgs\7cabb0eaf7571d5f7cd993e1afaab03.jpg" width = "300" alt="图片名称" align=center />

```tex
010011
111110
111111
111011
001111
111001
0b010011111110111111111011001111111001 = 21458039801
```

#### 我的实现

```java
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Main main = new Main3();
        long s = System.nanoTime();
        char[] res = main.solve("144, 0, 129, 131, 10, 139, 83, 84, 153, 29, 224, 172".split(", "));
        System.out.println((System.nanoTime() - s) / (1000d * 1000));
        System.out.println(res.length);
        for (char c : res) {
            int idx = (key >> 3);
            int step = (key & (((1 << 3) - 1)));
            step = ((step & 0b100) != 0) ? -(step & 0b11) - 1 : step + 1;
            System.out.println("第" + (idx + 1) + "块移动" + step + "步");
        }
    }

    //获取当前转态下的木块占用情况
    public long get_map_val(char[] block_values) {
        long map_val = 0;
        //解析每块木块信息
        for (char val : block_values) {
            boolean is_horizontal = (val & (1 << 7)) != 0;
            int len = ((val & (1 << 6)) >> 6) + 2;
            int row = (val >> 3) & ((1 << 3) - 1);
            int col = val & ((1 << 3) - 1);
            //获取木块占用情况
            long mask = 0;
            for (int i = 0; i < len; ++i) {
                mask <<= (is_horizontal ? 1 : 6);
                mask += 1;
            }
            //设置木块占用
            map_val |= (mask << (row * 6 + col));
        }
        //返回全部木块占用情况
        return map_val;
    }

    //检测是否逃脱完成
    public int check(char val, int step, long map_val) {
        //解析当前操作木块信息
        boolean is_horizontal = (val & (1 << 7)) != 0;
        int len = ((val & (1 << 6)) >> 6) + 2;
        int row = (val >> 3) & ((1 << 3) - 1);
        int col = val & ((1 << 3) - 1);
        //获取此操作后木块所在位置
        int new_s_col = col;
        int new_e_col = col + (is_horizontal ? step : 0);
        int new_s_row = row;
        int new_e_row = row + (is_horizontal ? 0 : step);
        //更新此操作后木块所在位置
        if (is_horizontal) {
            if (step < 0) {
                new_s_col = col + step;
                new_e_col = col + len;
            } else {
                new_e_col = col + len + step;
            }
        } else {
            if (step < 0) {
                new_s_row = row + step;
                new_e_row = row + len;
            } else {
                new_e_row = row + len + step;
            }
        }
        //判断操作是否会越界
        if (new_s_col < 0 || new_e_col > 6 || new_s_row < 0 || new_e_row > 6) return 0;
        //获取木块当前占用mask
        long mask_s = 0;
        for (int i = 0; i < len; ++i) {
            mask_s <<= (is_horizontal ? 1 : 6);
            mask_s += 1;
        }
        //获取此操作所需要的占用mask
        long mask_b = 0;
        for (int i = 0; i < len + Math.abs(step); ++i) {
            mask_b <<= (is_horizontal ? 1 : 6);
            mask_b += 1;
        }
        //清除当前木块占用
        map_val &= (~(mask_s << (row * 6 + col)));
        //判断可否执行当前操作
        if ((map_val & (mask_b << (new_s_row * 6 + new_s_col))) != 0) return 0;
        if (is_horizontal)
            col += step;
        else
            row += step;
        //获取执行后的占用情况
        map_val |= (mask_s << (row * 6 + col));
        //判断此操作后能否逃脱
        if (row == 2 && is_horizontal) {
            long mask_t = 0;
            for (int i = 0; i < 6 - (col + len); ++i) {
                mask_t <<= 1;
                mask_t += 1;
            }
            //此操作后能逃脱返回ob01111111
            if ((map_val & (mask_t << (row * 6 + col + len))) == 0) {
                return (1 << 8) - 1;
            }
        }
        //返回操作后此木块得最新信息
        int new_val = 1 << 8;
        new_val |= (is_horizontal ? 1 << 7 : 0);
        new_val |= ((len - 2) << 6);
        new_val |= (row << 3);
        new_val |= col;
        return new_val;
    }

    //获取新的结果
    private char[] get_new_res(char[] now_res, char val) {
        //拷贝之前的结果
        char[] res = Arrays.copyOf(now_res, now_res.length + 1);
        res[now_res.length] = val;
        return res;
    }

    //获取滑动路径
    private char[] solve(String[] input) {
        //存储出现过的转态,避免重复计算
        Set<String> hashSet = new HashSet<>();
        /*提取输入数据*/
        int len = input.length;
        char[] data = new char[len];
        len = 0;
        for (String s : input) {
            data[len++] = (char) Integer.parseInt(s);
        }
        //转态队列
        Queue<char[]> queue = new LinkedList<>();
        //结果队列
        Queue<char[]> queue_res = new LinkedList<>();
        queue.add(data);
        queue_res.add(new char[0]);
        hashSet.add(new String(data));
        //广度优先搜索
        while (!queue.isEmpty()) {
            //出队当前木块状态
            char[] now = queue.poll();
            //出队到达当前转态的路径
            char[] now_res = queue_res.poll();
            //获取当前转态木块占用情况
            long map_val = get_map_val(now);
            int[] dirs = {-1, 1};//木块尝试相两个方向移动
            //尝试移动每个木块
            for (int i = 0; i < len; i++) {
                for (int dir : dirs) {
                    //木块最多一次移动4步
                    for (int j = 1; j <= 4; ++j) {
                        //获取当前操作结果
                        int val = check(now[i], j * dir, map_val);
                        if (val != 0) {
                            //更新结果路径
                            char[] tem_res = dir < 0 ? get_new_res(now_res, (char) ((j + 3) | (i << 3))) : get_new_res(now_res, (char) ((j - 1) | (i << 3)));
                            //当前操作可逃脱成功
                            if (val == (1 << 8) - 1) {
                                //返回最优解结果
                                return tem_res;
                            }
                            //将操作后的转态入队
                            char[] tmp = Arrays.copyOf(now, len);
                            //只提取后八位
                            tmp[i] = (char) (val & ((1 << 8) - 1));
                            //转成String加速判断
                            String str = new String(tmp);
                            //如果操作后的状态已经存在则不处理
                            if (hashSet.contains(str)) {
                                continue;
                            }
                            queue.add(tmp);
                            queue_res.add(tem_res);
                            hashSet.add(str);
                        } else {
                            //操作不可行
                            break;
                        }
                    }
                }
            }
        }
        //找不到解
        return new char[0];
    }
}
```

### 3.效果

<img src=".\imgs\132a76a6-9e42-4a54-b2c5-5853ac344f48.gif" width = "300" alt="图片名称" align=center />
<img src=".\imgs\5bd0ea6c85ed592454894fb348499e8.png" width = "300" alt="图片名称" align=center />

