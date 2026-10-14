<template>
  <div>
    <el-upload
      ref="uploadRef"
      class="avatar-uploader"
      action="#"
      :auto-upload="false"
      :show-file-list="false"
      accept="image/jpeg,image/png"
      :on-change="handleChange"
    >
      <img v-if="imageUrl" :src="imageUrl" class="avatar" alt="本地头像预览" />
      <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
    </el-upload>
    <p class="tip">本地预览：JPG / PNG，不超过 2 MB；保存用户时不上传图片。</p>
  </div>
</template>

<script>
export default {
  name: 'AvatarUpload',
  data() {
    return { imageUrl: '' }
  },
  beforeUnmount() {
    this.releasePreview()
  },
  methods: {
    releasePreview() {
      if (this.imageUrl) URL.revokeObjectURL(this.imageUrl)
      this.imageUrl = ''
    },
    handleChange(uploadFile) {
      const file = uploadFile.raw
      if (!file) return
      const validType = ['image/jpeg', 'image/png'].includes(file.type)
      const validSize = file.size <= 2 * 1024 * 1024
      if (!validType || !validSize) {
        this.$message.error(!validType ? '只支持 JPG / PNG' : '图片不能超过 2 MB')
        this.$refs.uploadRef.clearFiles()
        return
      }
      this.releasePreview()
      this.imageUrl = URL.createObjectURL(file)
    }
  }
}
</script>

<style scoped>
.avatar-uploader :deep(.el-upload) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100px;
  height: 100px;
  overflow: hidden;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
}
.avatar-uploader :deep(.el-upload:hover) {
  border-color: #409eff;
}
.avatar {
  width: 100px;
  height: 100px;
  object-fit: cover;
}
.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
}
.tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>
