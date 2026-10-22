<!--
  收藏的政策：村民星标收藏的政策列表，点卡片进政策详情。
-->
<template>
  <div class="page">
    <van-nav-bar
      title="收藏的政策"
      left-text="返回"
      left-arrow
      fixed
      placeholder
      @click-left="goBack"
    />

    <div class="toolbar">
      <span>共 {{ total }} 条</span>
      <van-button plain round class="tool-btn" @click="reload">刷新</van-button>
    </div>

    <div v-if="loading" class="state-box">
      <van-loading size="24px">加载中...</van-loading>
    </div>
    <van-empty v-else-if="!list.length" description="暂无收藏" />
    <div v-else class="list">
      <div
        v-for="item in list"
        :key="item.id"
        class="card"
        @click="$router.push(`/policy/${item.id}`)"
      >
        <div class="row">
          <span class="star-badge" aria-hidden="true">
            <van-icon name="star" size="18" />
          </span>
          <strong>{{ item.title }}</strong>
          <van-icon name="arrow" color="#5a5a5a" />
        </div>
        <p v-if="item.standard" class="std-line">
          <van-icon name="gold-coin-o" />补贴标准：{{ item.standard }}
        </p>
        <p v-else>点击查看政策详情与申报入口</p>
      </div>
    </div>

    <div v-if="total > pageSize" class="pager">
      <van-pagination
        v-model="pageNum"
        :total-items="total"
        :items-per-page="pageSize"
        force-ellipses
        @change="load"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { fetchFavorites } from '@/api/villager'
import { safeBack } from '@/utils/navBack'

const router = useRouter()

function goBack() {
  safeBack(router, '/mine')
}

const pageSize = 5
const pageNum = ref(1)
const total = ref(0)
const list = ref([])
const loading = ref(false)

/** 拉取收藏（后端分页，每页 5 条） */
async function load() {
  loading.value = true
  try {
    const data = await fetchFavorites({ pageNum: pageNum.value, pageSize })
    list.value = data?.list || []
    total.value = Number(data?.total ?? list.value.length) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  pageNum.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #f3f6f1;
  padding-bottom: 24px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 4px;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
}
.tool-btn {
  min-height: 40px !important;
  padding: 0 14px !important;
  font-size: var(--xct-fs-tip, 0.9375rem) !important;
}
.state-box {
  padding: 40px 0;
  text-align: center;
}
.list {
  padding: 8px 16px;
}
/* 收藏卡：星标徽章 + 标题 + 补贴标准行 */
.card {
  background: var(--xct-card-bg, #fff);
  border: var(--xct-card-border, 0 solid transparent);
  border-radius: var(--xct-radius, 14px);
  padding: 14px 16px;
  margin-bottom: 12px;
  box-shadow: var(--xct-card-shadow, 0 4px 14px rgba(27, 94, 32, 0.06));
  min-height: var(--xct-hit-min, 48px);
}
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.star-badge {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--xct-gold-soft, #fff8e1);
  color: var(--xct-gold, #f9a825);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.card strong {
  flex: 1;
  min-width: 0;
  font-size: var(--xct-fs-body, 1rem);
  color: var(--xct-color-text, #222);
}
.card p {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 8px 0 0;
  font-size: var(--xct-fs-tip, 0.9375rem);
  color: var(--xct-color-muted, #5a5a5a);
  line-height: 1.45;
}
.std-line {
  color: var(--xct-green-deep, #1b5e20) !important;
  font-weight: 600;
}
.pager {
  padding: 12px 8px 24px;
}
</style>
