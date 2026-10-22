/** 银行列表常量（开户行下拉选项） */
export const BANK_OPTIONS = [
  { text: '中国工商银行', value: '中国工商银行' },
  { text: '中国农业银行', value: '中国农业银行' },
  { text: '中国银行', value: '中国银行' },
  { text: '中国建设银行', value: '中国建设银行' },
  { text: '交通银行', value: '交通银行' },
  { text: '中国邮政储蓄银行', value: '中国邮政储蓄银行' },
  { text: '招商银行', value: '招商银行' },
  { text: '中信银行', value: '中信银行' },
  { text: '浦发银行', value: '浦发银行' },
  { text: '民生银行', value: '民生银行' },
  { text: '兴业银行', value: '兴业银行' },
  { text: '光大银行', value: '光大银行' },
  { text: '华夏银行', value: '华夏银行' },
  { text: '平安银行', value: '平安银行' },
  { text: '广发银行', value: '广发银行' },
  { text: '北京银行', value: '北京银行' },
  { text: '上海银行', value: '上海银行' },
  { text: '江苏银行', value: '江苏银行' },
  { text: '浙商银行', value: '浙商银行' },
  { text: '南京银行', value: '南京银行' },
  { text: '宁波银行', value: '宁波银行' },
]

/** 银行选择器列配置（van-picker columns 格式，与种养选择器等一致使用对象数组） */
export const BANK_PICKER_COLUMNS = BANK_OPTIONS.map(({ text, value }) => ({ text, value }))