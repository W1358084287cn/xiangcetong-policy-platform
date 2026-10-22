<!--
  地区选择字段：只读输入框 + 底部县/乡镇/村三级级联弹层；
  选中后通过 update 事件回写三级名称。
-->
<template>
  <div class="region-fields">
    <van-field
      :model-value="displayText"
      label="所在地区"
      readonly
      is-link
      :placeholder="placeholder"
      :disabled="disabled"
      @click="open"
    />
    <van-popup v-model:show="show" teleport="body" position="bottom" round>
      <van-cascader
        v-model="cascaderValue"
        title="选择县 / 乡镇 / 村"
        :options="REGION_CASCADER_OPTIONS"
        active-color="#2e8b57"
        @close="show = false"
        @finish="onFinish"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { REGION_CASCADER_OPTIONS } from '@/constants/region'

const props = defineProps({
  countyName: { type: String, default: '' },
  townName: { type: String, default: '' },
  villageName: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '点击选择县乡镇村' },
})

const emit = defineEmits(['update:countyName', 'update:townName', 'update:villageName', 'change'])

const show = ref(false)
/** Cascader 选中的末级 value（村名） */
const cascaderValue = ref('')

/** 县/乡镇/村拼成一行展示文案 */
const displayText = computed(() => {
  const parts = [props.countyName, props.townName, props.villageName].filter(Boolean)
  return parts.join(' / ')
})

/** 用 props 里的村名同步级联选中值 */
function syncCascaderFromProps() {
  cascaderValue.value = props.villageName || ''
}

/** 打开地区选择弹层（禁用时不响应） */
function open() {
  if (props.disabled) return
  syncCascaderFromProps()
  show.value = true
}

/** 选完三级：selectedOptions 为 [县, 镇, 村] */
function onFinish({ selectedOptions }) {
  const county = selectedOptions[0]?.value || selectedOptions[0]?.text || ''
  const town = selectedOptions[1]?.value || selectedOptions[1]?.text || ''
  const village = selectedOptions[2]?.value || selectedOptions[2]?.text || ''
  emit('update:countyName', county)
  emit('update:townName', town)
  emit('update:villageName', village)
  emit('change', { countyName: county, townName: town, villageName: village })
  show.value = false
}

watch(
  () => [props.countyName, props.townName, props.villageName],
  syncCascaderFromProps,
  { immediate: true }
)
</script>

<style scoped>
/* 只读地区字段加大点击热区，老人好按 */
.region-fields :deep(.van-field) {
  min-height: var(--xct-hit-min, 48px);
  align-items: center;
}
</style>
