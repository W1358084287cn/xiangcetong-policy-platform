<!--
  个人资料表单：wizard 模式为七步分步向导（已不用于路由入口），
  默认整表编辑——基本信息/地区/主体/身份/种养规模/设施意向/银行卡七组一次看全。
-->
<template>
  <div class="profile-form">
    <div v-if="loading" class="state-box">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <!-- 分步向导：七步精细资料；不用横向 van-steps（七个标题必叠字） -->
    <template v-else-if="wizard">
      <div class="wiz-progress" aria-live="polite">
        <div class="wiz-progress-hd">
          <span class="wiz-progress-count">第 {{ wizardStep + 1 }}/{{ PROFILE_STEPS.length }} 步</span>
          <span class="wiz-progress-name">{{ currentStepLabel }}</span>
        </div>
        <div class="wiz-dots" role="list">
          <span
            v-for="(s, i) in PROFILE_STEPS"
            :key="s.key"
            role="listitem"
            class="wiz-dot"
            :class="{ done: i < wizardStep, active: i === wizardStep }"
            :title="s.label"
          />
        </div>
        <van-progress
          :percentage="Math.round(((wizardStep + 1) / PROFILE_STEPS.length) * 100)"
          stroke-width="6"
          color="#2e8b57"
          track-color="#e8efe6"
          :show-pivot="false"
        />
      </div>

      <div class="wiz-panel">
        <h4>{{ currentStepLabel }}</h4>
        <p class="wiz-tip">{{ currentStepTip }}</p>

        <template v-if="currentKey === 'region'">
          <div class="quick">
            <p class="quick-title">常用地区（点选）</p>
            <div class="quick-row">
              <van-button
                v-for="q in REGION_QUICK"
                :key="q.label"
                type="primary"
                plain
                round
                class="quick-btn"
                @click="applyQuick(q)"
              >
                {{ q.label }}
              </van-button>
            </div>
          </div>
          <van-field
            :model-value="regionDisplay"
            label="所在地区"
            is-link
            readonly
            placeholder="点击选择县/镇/村"
            @click="showRegionPicker = true"
          />
          <van-field v-model="form.countyName" label="县" placeholder="可点选或手填" />
          <van-field v-model="form.townName" label="乡镇" placeholder="可点选或手填" />
          <van-field v-model="form.villageName" label="村" placeholder="可点选或手填" />
        </template>

        <template v-else-if="currentKey === 'subject'">
          <!-- 自绘选项列表，避免 van-cell+字号放大导致行高塌陷叠字 -->
          <div class="opt-list" role="radiogroup" aria-label="经营主体">
            <button
              v-for="opt in SUBJECT_OPTIONS"
              :key="opt.value"
              type="button"
              class="opt-item"
              :class="{ active: form.subjectType === opt.value }"
              @click="form.subjectType = opt.value"
            >
              <span class="opt-text">{{ opt.text }}</span>
              <van-icon v-if="form.subjectType === opt.value" name="success" color="#2e8b57" />
            </button>
          </div>
          <van-field
            v-model="form.laborCount"
            type="digit"
            label="劳动力(人)"
            placeholder="家庭劳动力人数"
          />
          <van-field
            v-model="form.farmYears"
            type="digit"
            label="从业年限"
            placeholder="从事农业年数"
          />
        </template>

        <template v-else-if="currentKey === 'identity'">
          <p class="sub-label">是否退役军人家庭</p>
          <van-radio-group v-model="form.isVeteranFamily" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
          <p class="sub-label">是否脱贫监测户</p>
          <van-radio-group v-model="form.isMonitorHousehold" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
        </template>

        <template v-else-if="currentKey === 'plant'">
          <div class="opt-list" role="radiogroup" aria-label="种养类型">
            <button
              v-for="opt in plantColumns"
              :key="opt.value"
              type="button"
              class="opt-item"
              :class="{ active: form.plantType === opt.value }"
              @click="form.plantType = opt.value"
            >
              <span class="opt-text">{{ opt.text }}</span>
              <van-icon v-if="form.plantType === opt.value" name="success" color="#2e8b57" />
            </button>
          </div>
          <van-field
            :model-value="secondaryDisplay"
            label="兼营类型"
            is-link
            readonly
            placeholder="无兼营可跳过"
            @click="showSecondaryPicker = true"
          />
        </template>

        <template v-else-if="currentKey === 'scale'">
          <van-field
            v-model="form.plantArea"
            type="number"
            label="经营面积(亩)"
            placeholder="如 40"
          />
          <van-field
            v-model="form.irrigatedArea"
            type="number"
            label="水浇地(亩)"
            placeholder="没有填 0"
          />
          <p class="sub-label">是否有土地承包经营权证</p>
          <van-radio-group v-model="form.hasLandCert" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
          <template v-if="livePlant">
            <van-field
              :model-value="form.livestockType"
              label="养殖细分"
              is-link
              readonly
              placeholder="如生猪、肉牛"
              @click="showLivestockPicker = true"
            />
            <van-field
              v-model="form.livestockCount"
              type="digit"
              label="存栏数量"
              placeholder="头/只"
            />
          </template>
        </template>

        <template v-else-if="currentKey === 'facility'">
          <p class="sub-label">是否有购机或报废更新意向</p>
          <van-radio-group v-model="form.needMachinery" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
          <van-field
            v-if="Number(form.needMachinery) === 1"
            :model-value="form.machineryType"
            label="意向农机"
            is-link
            readonly
            placeholder="选择农机类型"
            @click="showMachineryPicker = true"
          />
          <p class="sub-label">是否已参保粮食保险</p>
          <van-radio-group v-model="form.hasGrainInsurance" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
          <p class="sub-label">是否有粪污处理设施</p>
          <van-radio-group v-model="form.hasManureFacility" direction="horizontal" class="yn-row">
            <van-radio :name="1">是</van-radio>
            <van-radio :name="0">否</van-radio>
          </van-radio-group>
        </template>

        <template v-else-if="currentKey === 'bank'">
          <van-field
            v-if="bankTailHint"
            :model-value="bankTailHint"
            label="已绑卡"
            readonly
          />
          <van-field
            :model-value="form.bankName"
            label="开户行"
            is-link
            readonly
            placeholder="请选择开户行"
            @click="showBankPicker = true"
          />
          <van-field
            v-model="form.bankCardInput"
            label="银行卡号 *"
            type="digit"
            maxlength="19"
            required
            :placeholder="bankTailHint ? '留空保留原卡；填写则更换' : '请输入银行卡号（必填）'"
          />
        </template>
      </div>

      <div class="wiz-actions">
        <van-button v-if="wizardStep > 0" round block plain :disabled="saving" @click="wizardStep--">
          上一步
        </van-button>
        <van-button
          round
          block
          type="primary"
          :loading="saving"
          class="main-btn"
          @click="onWizardNext"
        >
          {{ isLastStep ? '完成' : '下一步' }}
        </van-button>
      </div>
    </template>

    <!-- 整表编辑（非向导）：分组展示全部精细字段 -->
    <van-form v-else @submit="onSave">
      <van-cell-group inset title="基本信息">
        <van-field
          v-model="form.name"
          label="姓名"
          :readonly="!editing"
          placeholder="请输入姓名"
          maxlength="32"
        />
        <van-field
          v-model="form.phone"
          label="手机号"
          :readonly="!editing"
          placeholder="11 位手机号（登录账号）"
          maxlength="11"
          inputmode="numeric"
        />
        <van-field
          v-model="form.idNumber"
          label="身份证号"
          :readonly="!editing"
          placeholder="18 位身份证号码"
          maxlength="18"
        />
        <!-- 身份证正反面照片（非强制，填了申报时复用） -->
        <div class="id-photo-row">
          <div class="id-photo-item">
            <p class="id-photo-label">身份证正面</p>
            <van-uploader
              v-if="editing"
              :model-value="idFrontList"
              :max-count="1"
              :max-size="5 * 1024 * 1024"
              accept="image/jpeg,image/png,image/jpg"
              :after-read="(f) => onIdPhotoRead(f, 'idFront')"
              @delete="() => { form.idFront = '' }"
              @oversize="() => showToast('单张不超过 5MB')"
            />
            <img v-else-if="form.idFront" :src="form.idFront" class="id-photo-preview" alt="身份证正面" />
            <span v-else class="id-photo-empty">未上传</span>
          </div>
          <div class="id-photo-item">
            <p class="id-photo-label">身份证反面</p>
            <van-uploader
              v-if="editing"
              :model-value="idBackList"
              :max-count="1"
              :max-size="5 * 1024 * 1024"
              accept="image/jpeg,image/png,image/jpg"
              :after-read="(f) => onIdPhotoRead(f, 'idBack')"
              @delete="() => { form.idBack = '' }"
              @oversize="() => showToast('单张不超过 5MB')"
            />
            <img v-else-if="form.idBack" :src="form.idBack" class="id-photo-preview" alt="身份证反面" />
            <span v-else class="id-photo-empty">未上传</span>
          </div>
        </div>
        <div v-if="editing" class="phone-tip">修改手机号后，下次登录请使用新手机号</div>
      </van-cell-group>

      <van-cell-group inset title="所在地区">
        <van-field
          :model-value="regionDisplay"
          label="所在地区"
          :readonly="!editing"
          is-link
          placeholder="点击选择县/镇/村"
          @click="editing && (showRegionPicker = true)"
        />
        <van-field v-if="editing" v-model="form.countyName" label="县" />
        <van-field v-if="editing" v-model="form.townName" label="乡镇" />
        <van-field v-if="editing" v-model="form.villageName" label="村" />
      </van-cell-group>

      <van-cell-group inset title="经营主体">
        <van-field
          :model-value="subjectDisplay"
          label="主体类型"
          :readonly="!editing"
          is-link
          placeholder="点击选择"
          @click="editing && (showSubjectPicker = true)"
        />
        <van-field
          v-model="form.laborCount"
          type="digit"
          label="劳动力"
          :readonly="!editing"
          placeholder="人"
        />
        <van-field
          v-model="form.farmYears"
          type="digit"
          label="从业年限"
          :readonly="!editing"
          placeholder="年"
        />
      </van-cell-group>

      <van-cell-group inset title="身份情况">
        <van-field
          :model-value="ynLabel(form.isVeteranFamily)"
          label="退役军人家庭"
          :readonly="!editing"
          is-link
          placeholder="请选择"
          @click="editing && pickYn('isVeteranFamily')"
        />
        <van-field
          :model-value="ynLabel(form.isMonitorHousehold)"
          label="脱贫监测户"
          :readonly="!editing"
          is-link
          placeholder="请选择"
          @click="editing && pickYn('isMonitorHousehold')"
        />
      </van-cell-group>

      <van-cell-group inset title="种养与规模">
        <van-field
          v-model="form.plantType"
          label="种养类型"
          :readonly="!editing"
          is-link
          placeholder="点击选择"
          @click="editing && (showPlantPicker = true)"
        />
        <van-field
          :model-value="secondaryDisplay"
          label="兼营类型"
          :readonly="!editing"
          is-link
          placeholder="无兼营"
          @click="editing && (showSecondaryPicker = true)"
        />
        <van-field
          v-model="form.plantArea"
          type="number"
          label="经营面积"
          :readonly="!editing"
          placeholder="亩"
        />
        <van-field
          v-model="form.irrigatedArea"
          type="number"
          label="水浇地"
          :readonly="!editing"
          placeholder="亩"
        />
        <van-field
          :model-value="ynLabel(form.hasLandCert)"
          label="土地承包证"
          :readonly="!editing"
          is-link
          @click="editing && pickYn('hasLandCert')"
        />
        <template v-if="livePlant || form.livestockType">
          <van-field
            :model-value="form.livestockType"
            label="养殖细分"
            :readonly="!editing"
            is-link
            @click="editing && (showLivestockPicker = true)"
          />
          <van-field
            v-model="form.livestockCount"
            type="digit"
            label="存栏数量"
            :readonly="!editing"
          />
        </template>
      </van-cell-group>

      <van-cell-group inset title="设施与意向">
        <van-field
          :model-value="ynLabel(form.needMachinery)"
          label="购机意向"
          :readonly="!editing"
          is-link
          @click="editing && pickYn('needMachinery')"
        />
        <van-field
          v-if="Number(form.needMachinery) === 1 || form.machineryType"
          :model-value="form.machineryType"
          label="意向农机"
          :readonly="!editing"
          is-link
          @click="editing && (showMachineryPicker = true)"
        />
        <van-field
          :model-value="ynLabel(form.hasGrainInsurance)"
          label="粮食保险"
          :readonly="!editing"
          is-link
          @click="editing && pickYn('hasGrainInsurance')"
        />
        <van-field
          :model-value="ynLabel(form.hasManureFacility)"
          label="粪污设施"
          :readonly="!editing"
          is-link
          @click="editing && pickYn('hasManureFacility')"
        />
      </van-cell-group>

      <van-cell-group inset title="银行卡">
        <van-field
          :model-value="form.bankName"
          label="开户行"
          is-link
          readonly
          :placeholder="editing ? '请选择开户行' : (form.bankName || '未设置')"
          @click="editing && (showBankPicker = true)"
        />
        <van-field
          v-if="!editing && bankTailHint"
          :model-value="bankTailHint"
          label="银行卡 *"
          readonly
        />
        <van-field
          v-else
          v-model="form.bankCard"
          label="银行卡 *"
          :readonly="!editing"
          required
          :placeholder="bankTailHint ? '留空保留原卡；填写则更换' : '请填写银行卡号（必填）'"
        />
      </van-cell-group>

      <div v-if="editing && showQuick" class="quick">
        <p class="quick-title">常用地区（点选）</p>
        <div class="quick-row">
          <van-button
            v-for="q in REGION_QUICK"
            :key="q.label"
            type="primary"
            plain
            round
            class="quick-btn"
            @click="applyQuick(q)"
          >
            {{ q.label }}
          </van-button>
        </div>
      </div>

      <div v-if="editing" class="btn-wrap">
        <van-button
          round
          block
          type="primary"
          native-type="submit"
          :loading="saving"
          class="main-btn"
        >
          保存资料
        </van-button>
      </div>
    </van-form>

    <van-popup v-model:show="showPlantPicker" teleport="body" position="bottom" round>
      <van-picker :columns="plantColumns" @confirm="onPickPlant" @cancel="showPlantPicker = false" />
    </van-popup>
    <van-popup v-model:show="showSecondaryPicker" teleport="body" position="bottom" round>
      <van-picker
        :columns="SECONDARY_PLANT_OPTIONS"
        @confirm="onPickSecondary"
        @cancel="showSecondaryPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showSubjectPicker" teleport="body" position="bottom" round>
      <van-picker
        :columns="SUBJECT_OPTIONS"
        @confirm="onPickSubject"
        @cancel="showSubjectPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showLivestockPicker" teleport="body" position="bottom" round>
      <van-picker
        :columns="LIVESTOCK_TYPE_OPTIONS"
        @confirm="onPickLivestock"
        @cancel="showLivestockPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showMachineryPicker" teleport="body" position="bottom" round>
      <van-picker
        :columns="MACHINERY_TYPE_OPTIONS"
        @confirm="onPickMachinery"
        @cancel="showMachineryPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showYnPicker" teleport="body" position="bottom" round>
      <van-picker
        :columns="YES_NO_OPTIONS"
        @confirm="onPickYn"
        @cancel="showYnPicker = false"
      />
    </van-popup>
    <van-popup v-model:show="showRegionPicker" teleport="body" position="bottom" round>
      <van-cascader
        v-model="cascaderValue"
        title="选择所在地区"
        :options="regionColumns"
        @close="showRegionPicker = false"
        @finish="onPickRegion"
      />
    </van-popup>
    <van-popup v-model:show="showBankPicker" teleport="body" position="bottom" round :lock-scroll="true">
      <van-picker
        :columns="BANK_PICKER_COLUMNS"
        @confirm="onPickBank"
        @cancel="showBankPicker = false"
        title="选择开户行"
      />
    </van-popup>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { showSuccessToast, showLoadingToast, showToast } from 'vant'
import { fetchProfile, updateProfile } from '@/api/villager'
import { getUser, setUser } from '@/utils/auth'
import { uploadFile } from '@/api/file'
import { compressImage } from '@/utils/imageCompress'
import { PLANT_PICKER_COLUMNS, SECONDARY_PLANT_OPTIONS, isLiveGroupDone } from '@/constants/plantType'
import { REGION_QUICK, buildRegionColumns } from '@/constants/region'
import {
  SUBJECT_OPTIONS,
  LIVESTOCK_TYPE_OPTIONS,
  MACHINERY_TYPE_OPTIONS,
  YES_NO_OPTIONS,
  subjectLabel,
  ynLabel,
} from '@/constants/profileOptions'
import { PROFILE_STEPS, isStepDone, missingProfileFields } from '@/utils/profileGate'
import { BANK_PICKER_COLUMNS } from '@/constants/bank'

const props = defineProps({
  editing: { type: Boolean, default: undefined },
  wizard: { type: Boolean, default: false },
  showQuick: { type: Boolean, default: true },
})

const emit = defineEmits(['update:editing', 'saved', 'loaded', 'wizard-done'])

const loading = ref(false)
const saving = ref(false)
const innerEditing = ref(false)
const showPlantPicker = ref(false)
const showSecondaryPicker = ref(false)
const showSubjectPicker = ref(false)
const showLivestockPicker = ref(false)
const showMachineryPicker = ref(false)
const showYnPicker = ref(false)
const ynField = ref('')
const showRegionPicker = ref(false)
const showBankPicker = ref(false)
const cascaderValue = ref('')
const wizardStep = ref(0)

/** 是否编辑中：支持父组件 v-model 或组件内自管 */
const editing = computed({
  get: () => (props.editing !== undefined ? props.editing : innerEditing.value),
  set: (v) => {
    if (props.editing !== undefined) emit('update:editing', v)
    else innerEditing.value = v
  },
})

const plantColumns = PLANT_PICKER_COLUMNS
const regionColumns = buildRegionColumns()

const form = reactive({
  name: '',
  phone: '',
  idNumber: '',
  idFront: '',
  idBack: '',
  countyName: '',
  townName: '',
  villageName: '',
  subjectType: '',
  laborCount: '',
  farmYears: '',
  isVeteranFamily: null,
  isMonitorHousehold: null,
  plantType: '',
  secondaryPlantType: '',
  plantArea: '',
  irrigatedArea: '',
  hasLandCert: null,
  livestockType: '',
  livestockCount: '',
  needMachinery: null,
  machineryType: '',
  hasGrainInsurance: null,
  hasManureFacility: null,
  bankName: '',
  bankCard: '',
  bankCardInput: '',
})

/** 当前向导步 key */
const currentKey = computed(() => PROFILE_STEPS[wizardStep.value]?.key || 'region')
/** 当前向导步标题 */
const currentStepLabel = computed(() => PROFILE_STEPS[wizardStep.value]?.label || '')
/** 是否最后一步 */
const isLastStep = computed(() => wizardStep.value >= PROFILE_STEPS.length - 1)
/** 种养是否养殖类（规模步需填存栏） */
const livePlant = computed(() => isLiveGroupDone(form.plantType))

/** 当前步口语提示（适老短句） */
const currentStepTip = computed(() => {
  const tips = {
    region: '这一步选您住在哪——县、镇、村点一下就行',
    subject: '这一步选您是农户、家庭农场还是合作社，再填劳动力人数',
    identity: '这一步如实选：是否义务兵家庭、是否监测户',
    plant: '这一步填种的什么、养的什么；有兼营可再选一项',
    scale: '这一步填种了多少亩、有没有土地证；养畜禽的再填存栏',
    facility: '这一步选要不要买农机、有没有粮食保险和粪污设施',
    bank: '这一步填开户行和银行卡号，补贴打到这张卡',
  }
  return tips[currentKey.value] || ''
})

const regionDisplay = computed(() => {
  const parts = [form.countyName, form.townName, form.villageName].filter(Boolean)
  return parts.length ? parts.join(' · ') : ''
})

const subjectDisplay = computed(() => subjectLabel(form.subjectType))
const secondaryDisplay = computed(() => form.secondaryPlantType || '无兼营')

/** 已绑卡尾号提示 */
const bankTailHint = computed(() => {
  const card = String(form.bankCard || '')
  if (!card) return ''
  if (card.includes('*')) {
    const tail = card.replace(/\*/g, '').slice(-4)
    return tail ? `已绑定尾号 ${tail}` : '已绑定银行卡'
  }
  if (!editing.value && !props.wizard && card.length >= 4) {
    return `已绑定尾号 ${card.slice(-4)}`
  }
  if (props.wizard && card.length >= 4 && !form.bankCardInput) {
    return `已绑定尾号 ${card.slice(-4)}`
  }
  return ''
})

watch(
  () => props.editing,
  (v) => {
    if (v && form.bankCard?.includes('*')) form.bankCard = ''
  }
)

watch(
  () => props.wizard,
  (v) => {
    if (v) {
      jumpToFirstMissing()
      form.bankCardInput = ''
    }
  },
  { immediate: true }
)

/** 跳到第一个未完成步骤 */
function jumpToFirstMissing() {
  const miss = missingProfileFields(form)
  const idx = miss.length ? PROFILE_STEPS.findIndex((s) => s.key === miss[0]) : 0
  wizardStep.value = idx >= 0 ? idx : 0
}

/** 身份证正反面照片 URL → Vant Uploader 需要的文件列表 */
const idFrontList = computed(() => {
  if (!form.idFront) return []
  return [{ url: form.idFront, isImage: true }]
})
const idBackList = computed(() => {
  if (!form.idBack) return []
  return [{ url: form.idBack, isImage: true }]
})

/** 身份证照片先本地压缩再上传，写入 form */
async function onIdPhotoRead(file, key) {
  const item = Array.isArray(file) ? file[0] : file
  item.status = 'uploading'
  let toast
  try {
    // 本地压缩：手机原图动辄数 MB，先缩到长边 1600px 再传，显著提速
    const compressed = await compressImage(item.file)
    toast = showLoadingToast({ message: '上传中 0%', forbidClick: true, duration: 0 })
    const res = await uploadFile(compressed, (p) => {
      if (toast) toast.message = `上传中 ${p}%`
    })
    const url = res?.url || res
    form[key] = url
    item.url = url
    item.status = 'done'
  } catch {
    item.status = 'failed'
    showToast('上传失败')
  } finally {
    if (toast) toast.close()
  }
}

/** 0/1 规范化写入表单 */
function toYn(v) {
  if (v === 0 || v === 1) return v
  if (v === '0' || v === '1') return Number(v)
  return null
}

/** 把接口资料写入表单 */
function fillForm(data) {
  const user = getUser()
  form.name = data?.name || user?.name || ''
  form.phone = data?.phone || ''
  form.idNumber = data?.idNumber || ''
  form.idFront = data?.idFront || ''
  form.idBack = data?.idBack || ''
  form.countyName = data?.countyName || ''
  form.townName = data?.townName || ''
  form.villageName = data?.villageName || ''
  form.subjectType = data?.subjectType || ''
  form.laborCount = data?.laborCount != null ? String(data.laborCount) : ''
  form.farmYears = data?.farmYears != null ? String(data.farmYears) : ''
  form.isVeteranFamily = toYn(data?.isVeteranFamily)
  form.isMonitorHousehold = toYn(data?.isMonitorHousehold)
  form.plantType = data?.plantType || ''
  form.secondaryPlantType = data?.secondaryPlantType || ''
  form.plantArea = data?.plantArea != null ? String(data.plantArea) : ''
  form.irrigatedArea = data?.irrigatedArea != null ? String(data.irrigatedArea) : ''
  form.hasLandCert = toYn(data?.hasLandCert)
  form.livestockType = data?.livestockType || ''
  form.livestockCount = data?.livestockCount != null ? String(data.livestockCount) : ''
  form.needMachinery = toYn(data?.needMachinery)
  form.machineryType = data?.machineryType || ''
  form.hasGrainInsurance = toYn(data?.hasGrainInsurance)
  form.hasManureFacility = toYn(data?.hasManureFacility)
  form.bankName = data?.bankName || ''
  form.bankCard = data?.bankCard || ''
}

/** 拉取村民资料 */
async function loadProfile() {
  loading.value = true
  try {
    const data = await fetchProfile()
    fillForm(data)
    emit('loaded', { ...form })
    if (props.wizard) jumpToFirstMissing()
  } catch {
    fillForm({})
  } finally {
    loading.value = false
  }
}

/** 种养选择器确认：写入 plantType */
function onPickPlant({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (opt) form.plantType = opt.value
  showPlantPicker.value = false
}

/** 兼营选择器确认：空值表示无兼营 */
function onPickSecondary({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  form.secondaryPlantType = opt?.value || ''
  showSecondaryPicker.value = false
}

/** 经营主体选择器确认 */
function onPickSubject({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (opt) form.subjectType = opt.value
  showSubjectPicker.value = false
}

/** 养殖细分选择器确认 */
function onPickLivestock({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (opt) form.livestockType = opt.value
  showLivestockPicker.value = false
}

/** 意向农机选择器确认 */
function onPickMachinery({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (opt) form.machineryType = opt.value
  showMachineryPicker.value = false
}

/** 打开是否选择器，记录当前要写的字段名 */
function pickYn(field) {
  ynField.value = field
  showYnPicker.value = true
}

/** 是否选择器确认：把 0/1 写回 ynField 对应字段 */
function onPickYn({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (ynField.value && opt) form[ynField.value] = opt.value
  showYnPicker.value = false
}

/** 地区级联选完：写入县/乡镇/村 */
function onPickRegion({ selectedOptions }) {
  const [c, t, v] = selectedOptions || []
  if (c) form.countyName = c.value || c.text
  if (t) form.townName = t.value || t.text
  if (v) form.villageName = v.value || v.text
  showRegionPicker.value = false
}

/** 银行选择确认 */
function onPickBank({ selectedOptions }) {
  const opt = selectedOptions?.[0]
  if (opt) form.bankName = opt.text
  showBankPicker.value = false
}

/** 点选常用地区快捷填入县乡镇村 */
function applyQuick(q) {
  form.countyName = q.countyName
  form.townName = q.townName
  form.villageName = q.villageName
}

/** yn 是否已选 */
function ynOk(v) {
  return v === 0 || v === 1
}

/** 校验当前向导步并组装增量 payload */
function buildStepPayload() {
  const key = currentKey.value
  if (key === 'region') {
    if (!form.countyName?.trim() || !form.townName?.trim() || !form.villageName?.trim()) {
      showToast('请完善县、乡镇、村')
      return null
    }
    return {
      countyName: form.countyName.trim(),
      townName: form.townName.trim(),
      villageName: form.villageName.trim(),
    }
  }
  if (key === 'subject') {
    if (!form.subjectType) {
      showToast('请选择经营主体')
      return null
    }
    const labor = Number(form.laborCount)
    const years = Number(form.farmYears)
    if (form.laborCount === '' || Number.isNaN(labor) || labor < 0 || !Number.isInteger(labor)) {
      showToast('请填写有效的劳动力人数（整数）')
      return null
    }
    if (form.farmYears === '' || Number.isNaN(years) || years < 0 || !Number.isInteger(years)) {
      showToast('请填写有效的从业年限（整数）')
      return null
    }
    return {
      subjectType: String(form.subjectType),
      laborCount: labor,
      farmYears: years,
    }
  }
  if (key === 'identity') {
    if (!ynOk(form.isVeteranFamily) || !ynOk(form.isMonitorHousehold)) {
      showToast('请选择身份情况')
      return null
    }
    return {
      isVeteranFamily: form.isVeteranFamily,
      isMonitorHousehold: form.isMonitorHousehold,
    }
  }
  if (key === 'plant') {
    if (!form.plantType) {
      showToast('请选择种养类型')
      return null
    }
    return {
      plantType: form.plantType,
      secondaryPlantType: form.secondaryPlantType || '',
    }
  }
  if (key === 'scale') {
    if (form.plantArea === '' || Number(form.plantArea) < 0) {
      showToast('请填写有效经营面积')
      return null
    }
    if (form.irrigatedArea === '' || Number(form.irrigatedArea) < 0) {
      showToast('请填写水浇地面积（没有填 0）')
      return null
    }
    if (!ynOk(form.hasLandCert)) {
      showToast('请选择是否有土地承包证')
      return null
    }
    const payload = {
      plantArea: Number(form.plantArea),
      irrigatedArea: Number(form.irrigatedArea),
      hasLandCert: form.hasLandCert,
    }
    if (livePlant.value) {
      if (!form.livestockType) {
        showToast('请选择养殖细分')
        return null
      }
      if (form.livestockCount === '' || Number(form.livestockCount) < 0) {
        showToast('请填写存栏数量')
        return null
      }
      payload.livestockType = form.livestockType
      payload.livestockCount = Number(form.livestockCount)
    }
    return payload
  }
  if (key === 'facility') {
    if (!ynOk(form.needMachinery) || !ynOk(form.hasGrainInsurance) || !ynOk(form.hasManureFacility)) {
      showToast('请完善设施与意向选项')
      return null
    }
    const payload = {
      needMachinery: form.needMachinery,
      hasGrainInsurance: form.hasGrainInsurance,
      hasManureFacility: form.hasManureFacility,
    }
    if (Number(form.needMachinery) === 1) {
      if (!form.machineryType) {
        showToast('请选择意向农机类型')
        return null
      }
      payload.machineryType = form.machineryType
    } else {
      payload.machineryType = ''
    }
    return payload
  }
  if (key === 'bank') {
    const bankName = String(form.bankName || '').trim()
    if (!bankName) {
      showToast('请选择开户行')
      return null
    }
    const input = String(form.bankCardInput || '').trim()
    const payload = { bankName }
    if (input) {
      if (input.length < 12) {
        showToast('请填写完整银行卡号（至少12位）')
        return null
      }
      payload.bankCard = input
    } else if (!form.bankCard) {
      showToast('请填写银行卡号（必填）')
      return null
    } else {
      payload.bankCard = form.bankCard
    }
    return payload
  }
  return null
}

/** 向导下一步/完成 */
async function onWizardNext() {
  const payload = buildStepPayload()
  if (!payload) return
  saving.value = true
  try {
    fillForm(await updateProfile(payload))
    form.bankCardInput = ''
    if (isLastStep.value) {
      showSuccessToast('资料已完善')
      emit('saved', { ...form })
      emit('wizard-done', { ...form })
    } else {
      showSuccessToast('已保存')
      wizardStep.value += 1
    }
  } catch {
    // Toast 已处理
  } finally {
    saving.value = false
  }
}

/** 整表保存 */
async function onSave() {
  const nameTrim = String(form.name || '').trim()
  const phoneTrim = String(form.phone || '').trim()
  if (!nameTrim) {
    showToast('请填写姓名')
    return
  }
  if (nameTrim.length > 32) {
    showToast('姓名不能超过 32 个字')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(phoneTrim)) {
    showToast('手机号格式不正确')
    return
  }
  if (!isStepDone(form, 'region')) {
    showToast('请完善地区')
    return
  }
  if (!isStepDone(form, 'subject')) {
    showToast('请完善经营主体')
    return
  }
  if (!isStepDone(form, 'identity')) {
    showToast('请完善身份情况')
    return
  }
  if (!isStepDone(form, 'plant')) {
    showToast('请选择种养类型')
    return
  }
  if (!isStepDone(form, 'scale')) {
    showToast('请完善经营规模')
    return
  }
  if (!isStepDone(form, 'facility')) {
    showToast('请完善设施意向')
    return
  }
  if (!form.bankName?.trim()) {
    showToast('请选择开户行')
    return
  }
  const bankCardVal = String(form.bankCard || '').trim()
  if (!bankCardVal || bankCardVal.includes('*')) {
    showToast('请填写银行卡号（必填）')
    return
  }
  if (bankCardVal.length < 12) {
    showToast('请填写完整银行卡号（至少12位）')
    return
  }

  saving.value = true
  try {
    const payload = {
      name: nameTrim,
      phone: phoneTrim,
      countyName: form.countyName.trim(),
      townName: form.townName.trim(),
      villageName: form.villageName.trim(),
      subjectType: form.subjectType,
      laborCount: Number(form.laborCount),
      farmYears: Number(form.farmYears),
      isVeteranFamily: form.isVeteranFamily,
      isMonitorHousehold: form.isMonitorHousehold,
      plantType: form.plantType,
      secondaryPlantType: form.secondaryPlantType || '',
      plantArea: Number(form.plantArea),
      irrigatedArea: Number(form.irrigatedArea),
      hasLandCert: form.hasLandCert,
      needMachinery: form.needMachinery,
      hasGrainInsurance: form.hasGrainInsurance,
      hasManureFacility: form.hasManureFacility,
      bankName: form.bankName.trim(),
      idNumber: String(form.idNumber || '').trim() || null,
      idFront: String(form.idFront || '').trim() || null,
      idBack: String(form.idBack || '').trim() || null,
    }
    if (livePlant.value) {
      payload.livestockType = form.livestockType
      payload.livestockCount = Number(form.livestockCount)
    }
    if (Number(form.needMachinery) === 1) {
      payload.machineryType = form.machineryType
    } else {
      payload.machineryType = ''
    }
    if (form.bankCard && !String(form.bankCard).includes('*')) {
      payload.bankCard = form.bankCard
    }
    fillForm(await updateProfile(payload))
    // 姓名/手机号变了 → 同步登录缓存，否则「我的」顶部仍显示旧姓名
    const cached = getUser()
    if (cached) {
      setUser({ ...cached, name: nameTrim, phone: phoneTrim })
    }
    editing.value = false
    showSuccessToast('保存成功')
    emit('saved', { ...form })
  } catch {
    // Toast 已处理
  } finally {
    saving.value = false
  }
}

defineExpose({ loadProfile, editing, isStepDone: (k) => isStepDone(form, k) })

onMounted(loadProfile)
</script>

<style scoped>
/* 整表分组：标题加绿点标识，分组卡统一圆角阴影 */
.profile-form :deep(.van-cell-group__title) {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 700;
  color: var(--xct-green-deep, #1b5e20);
}
.profile-form :deep(.van-cell-group__title)::before {
  content: '';
  width: 6px;
  height: 16px;
  border-radius: 3px;
  background: var(--xct-green, #2e8b57);
}
.profile-form :deep(.van-cell-group--inset) {
  border-radius: var(--xct-radius, 14px);
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
}
/* 向导面板与进度卡统一卡片语言 */
.phone-tip {
  padding: 4px 16px 10px;
  font-size: var(--xct-fs-tip, 0.8125rem);
  color: var(--xct-color-muted, #5a5a5a);
}
.id-photo-row {
  display: flex;
  gap: 12px;
  padding: 8px 16px 4px;
}
.id-photo-item {
  flex: 1;
}
.id-photo-label {
  margin: 0 0 4px;
  font-size: var(--xct-fs-label, 0.875rem);
  color: var(--xct-text-secondary, #646566);
}
.id-photo-preview {
  width: 100%;
  max-height: 120px;
  object-fit: contain;
  border-radius: 8px;
  border: 1px solid #e5e5e5;
}
.id-photo-empty {
  display: block;
  font-size: 0.8125rem;
  color: var(--xct-color-muted, #999);
  padding: 8px 0;
}
.state-box {
  padding: 24px;
  text-align: center;
}
.btn-wrap {
  margin: 16px;
}
.main-btn {
  height: 48px !important;
  font-size: 17px !important;
}
.quick {
  margin: 0 0 12px;
  padding: 12px;
  background: #f7faf5;
  border-radius: 12px;
}
.quick-title {
  margin: 0 0 10px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
}
.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.quick-btn {
  min-height: var(--xct-hit-min, 48px) !important;
  padding: 0 16px !important;
  font-size: var(--xct-fs-tip, 0.9375rem) !important;
}
.wiz-progress {
  margin: 8px 12px 16px;
  padding: 14px 14px 12px;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
}
.wiz-progress-hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}
.wiz-progress-count {
  flex-shrink: 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  font-weight: 700;
  color: #2e8b57;
  white-space: nowrap;
}
.wiz-progress-name {
  font-size: var(--xct-fs-body, 1rem);
  font-weight: 600;
  color: #1b5e20;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wiz-dots {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  margin-bottom: 10px;
}
.wiz-dot {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: #e0e6dc;
}
.wiz-dot.done {
  background: #81c784;
}
.wiz-dot.active {
  background: #2e8b57;
}
.wiz-panel {
  margin: 0 16px;
  padding: 16px;
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
}
.wiz-panel h4 {
  margin: 0 0 6px;
  font-size: var(--xct-fs-title, 1.25rem);
  color: #1b5e20;
  text-align: center;
}
.wiz-tip {
  margin: 0 0 14px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
  text-align: center;
  line-height: 1.45;
}
.sub-label {
  margin: 12px 0 8px;
  font-size: var(--xct-fs-body, 1rem);
  color: #333;
}
.yn-row {
  margin: 0 0 8px 12px;
  gap: 24px;
}
.opt-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}
.opt-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: var(--xct-hit-min, 48px);
  padding: 12px 14px;
  border: 1px solid #e0e6dc;
  border-radius: 10px;
  background: #fff;
  color: #333;
  font-size: var(--xct-fs-body, 1rem);
  line-height: 1.45;
  text-align: left;
  cursor: pointer;
}
.opt-item .opt-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}
.opt-item.active {
  border-color: #2e8b57;
  background: #f1f8f2;
  color: #1b5e20;
  font-weight: 600;
}
.wiz-actions {
  margin: 20px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
</style>