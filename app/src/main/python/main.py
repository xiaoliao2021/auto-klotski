import cv2 as cv
import numpy as np
import copy
import os
import time as mytime


def parse_block(val):
    is_horizontal = (val & (1 << 7)) != 0
    len_ = ((val & (1 << 6)) >> 6) + 2
    row = (val >> 3) & (2 ** 3 - 1)
    col = val & (2 ** 3 - 1)
    return Block(row, col, len_, is_horizontal)


class Block:
    def __init__(self, row, col, len_, is_horizontal):
        self.row = row
        self.col = col
        self.len_ = len_
        self.is_horizontal = is_horizontal

    def __str__(self):
        return f'{self.row},{self.col},{self.len_},{self.is_horizontal}'

    def __repr__(self):
        return self.__str__()

    def value(self):
        val = 0
        val |= ((1 << 7) if self.is_horizontal else 0)
        val |= ((self.len_ - 2) << 6)
        val |= (self.row << 3)
        val |= self.col
        return val


def check_map(block_val, step, map_val):
    block_ = parse_block(block_val)
    new_s_col = block_.col
    new_e_col = block_.col + (step if block_.is_horizontal else 0)
    new_s_row = block_.row
    new_e_row = block_.row + (0 if block_.is_horizontal else step)
    if block_.is_horizontal:
        new_s_col = block_.col + step
        new_e_col = new_s_col + block_.len_
    else:
        new_s_row = block_.row + step
        new_e_row = new_s_row + block_.len_
    if new_s_col < 0 or new_e_col > 6 or new_s_row < 0 or new_e_row > 6:
        return None
    mask = 0
    for i in range(block_.len_):
        mask <<= (1 if block_.is_horizontal else 6)
        mask += 1
    map_val &= (~(mask << (block_.row * 6 + block_.col)))
    if map_val & (mask << (new_s_row * 6 + new_s_col)) != 0:
        return None
    if block_.is_horizontal:
        block_.col += step
    else:
        block_.row += step

    map_val |= (mask << (block_.row * 6 + block_.col))
    return block_.value(), map_val


def get_map(block_values):
    map_val = 0
    for block_val in block_values:
        block_ = parse_block(block_val)
        mask = 0
        for i in range(block_.len_):
            mask <<= (1 if block_.is_horizontal else 6)
            mask += 1
        map_val |= (mask << (block_.row * 6 + block_.col))
    return map_val


def show_blocks(blocks, target_idx=0, item_len=120):
    temp = np.ones((6 * item_len, 6 * item_len, 3), np.uint8)
    temp[:] = [17, 40, 66]
    for i in range(1, 6):
        temp[i * item_len, :] = 0
        temp[:, i * item_len] = 0
    for idx, block in enumerate(blocks):
        row, col, len_, is_horizontal = block.row, block.col, block.len_, block.is_horizontal
        row_num = 1 if is_horizontal else len_
        col_num = len_ if is_horizontal else 1
        color = [13, 25, 185] if idx == target_idx else [121, 210, 254]
        temp[row * item_len + 1:(row + row_num) * item_len - 1,
        col * item_len + 1:(col + col_num) * item_len - 1] = color
    cv.imshow('11', temp)
    cv.waitKey(200)


def get_start_point(y, x, h, w):
    return y + min(h, w) // 2, x + min(h, w) // 2, round(
        max(w / h, h / w)), True if w > h else False


def get_input(image_path, position):
    img = cv.imread(image_path, cv.IMREAD_GRAYSCALE)
    s_x, s_y, s_w, s_h = position
    temp = img[s_y:s_y + s_h, s_x:s_x + s_w]
    template = cv.imread("/storage/emulated/0/Android/data/com.ying.python_demo/files/template.jpg",
                         cv.IMREAD_GRAYSCALE)
    # 获得模板图片的高宽尺寸
    template_h, template_w = template.shape[:2]
    result = cv.matchTemplate(temp, template, cv.TM_SQDIFF_NORMED)
    # 归一化处理
    cv.normalize(result, result, 0, 1, cv.NORM_MINMAX, -1)
    # 寻找矩阵（一维数组当做向量，用Mat定义）中的最大值和最小值的匹配结果及其位置
    min_val, max_val, min_loc, max_loc = cv.minMaxLoc(result)
    input_list = []
    start_points = []
    start_point = get_start_point(min_loc[1], min_loc[0], template_h, template_w)
    start_points.append(start_point)
    temp[min_loc[1]:min_loc[1] + template_h, min_loc[0]:min_loc[0] + template_w] = 0
    _, binary = cv.threshold(temp, 80, 255, cv.THRESH_BINARY)
    ret, labels = cv.connectedComponents(binary, connectivity=8)
    for i in range(1, ret):
        temp2 = np.zeros_like(binary)
        temp2[labels == i] = 255
        contours, _ = cv.findContours(temp2, cv.RETR_EXTERNAL, cv.CHAIN_APPROX_SIMPLE)
        x, y, w, h = cv.boundingRect(contours[0])
        start_point = get_start_point(y, x, h, w)
        start_points.append(start_point)
    item_len = temp.shape[0] / 6
    for y, x, l, dir_ in start_points:
        row, col = int(y // item_len), int(x // item_len)
        input_list.append([row, col, l, dir_])
    return input_list

def get_input_list(src_path):
    position = (45, 300, 673 - 45, 930 - 300)
    input_list = get_input(src_path, position)
    list_res = []
    for r, c, l, h in input_list:
        block = Block(r, c, l, h)
        list_res.append(block.value())
    return str(list_res)


def main(path):
    block_values = []
    # src_path = '/storage/emulated/0/Android/data/com.ying.python_demo/files/image.png'
    src_path = path
    position = (45, 300, 673 - 45, 930 - 300)
    input_list = get_input(src_path, position)
    step_len = (position[2] + position[3]) // 12
    target_idx = 0
    for r, c, l, h in input_list:
        block = Block(r, c, l, h)
        block_values.append(block.value())
    map_val = get_map(block_values)
    stack = [(block_values, map_val, [])]
    map_cache = []
    res_list = []
    while len(stack):
        cur_block_values, cur_map_val, res = stack.pop(0)
        target_block = parse_block(cur_block_values[target_idx])
        if target_block.col == 4:
            res_list = res
            break
        if cur_map_val in map_cache:
            continue
        map_cache.append(cur_map_val)
        for idx, block_val in enumerate(cur_block_values):
            for step in range(1, 5):
                ret = check_map(block_val, step, cur_map_val)
                if ret is None or ret[1] in map_cache:
                    break
                new_block_values = copy.deepcopy(cur_block_values)
                new_block_values[idx] = ret[0]
                new_res = copy.deepcopy(res)
                new_res.append([idx, step])
                stack.append((new_block_values, ret[1], new_res))
            for step in range(1, 5):
                ret = check_map(block_val, -step, cur_map_val)
                if ret is None or ret[1] in map_cache:
                    break
                new_block_values = copy.deepcopy(cur_block_values)
                new_block_values[idx] = ret[0]
                new_res = copy.deepcopy(res)
                new_res.append([idx, -step])
                stack.append((new_block_values, ret[1], new_res))
    print(len(res_list) - 1)
    print(res_list)

    blocks = [parse_block(val) for val in block_values]
    res_str = ''
    for i, (idx, step) in enumerate(res_list):
        op = blocks[idx]
        s_x = e_x = int(position[0] + (op.col + 0.5) * step_len)
        s_y = e_y = int(position[1] + (op.row + 0.5) * step_len)
        time = 500 if i != 0 else 600
        if op.is_horizontal:
            op.col += step
            e_x = int(position[0] + (op.col + 0.5) * step_len)
        else:
            op.row += step
            e_y = int(position[1] + (op.row + 0.5) * step_len)
        # res_str += f'{s_x} {s_y} {e_x} {e_y} {time},'
        res_str += f'{idx} {step},'
    return res_str[:-1]
