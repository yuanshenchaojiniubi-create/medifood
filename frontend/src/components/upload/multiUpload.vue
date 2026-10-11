<!-- <template>
  <div>
    <el-upload
      action="http://gulimall-yuanshen.oss-cn-beijing.aliyuncs.com"
      :data="dataObj"
      list-type="picture-card"
      :file-list="fileList"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      :on-success="handleUploadSuccess"
      :on-preview="handlePreview"
      :limit="maxCount"
      :on-exceed="handleExceed"
    >
      <i class="el-icon-plus"></i>
    </el-upload>
    <el-dialog :visible.sync="dialogVisible">
      <img width="100%" :src="dialogImageUrl" alt />
    </el-dialog>
  </div>
</template>
<script>
import { policy } from "./policy";
import { getUUID } from '@/utils'
export default {
  name: "multiUpload",
  props: {
    //图片属性数组
    value: Array,
    //最大上传图片数量
    maxCount: {
      type: Number,
      default: 30
    }
  },
  data() {
    return {
      dataObj: {
        policy: "",
        signature: "",
        key: "",
        ossaccessKeyId: "",
        dir: "",
        host: "",
        uuid: ""
      },
      dialogVisible: false,
      dialogImageUrl: null
    };
  },
  computed: {
    fileList() {
      let fileList = [];
      for (let i = 0; i < this.value.length; i++) {
        fileList.push({ url: this.value[i] });
      }

      return fileList;
    }
  },
  mounted() {},
  methods: {
    emitInput(fileList) {
      let value = [];
      for (let i = 0; i < fileList.length; i++) {
        value.push(fileList[i].url);
      }
      this.$emit("input", value);
    },
    handleRemove(file, fileList) {
      this.emitInput(fileList);
    },
    handlePreview(file) {
      this.dialogVisible = true;
      this.dialogImageUrl = file.url;
    },
    beforeUpload(file) {
      let _self = this;
      return new Promise((resolve, reject) => {
        policy()
          .then(response => {
            console.log("这是什么${filename}");
             
          // 设置OSS V4签名所需字段
          _self.dataObj.host = response.host || '';
          _self.dataObj.policy = response.policy || '';
          _self.dataObj['x-oss-signature'] = response.signature || '';
          _self.dataObj['x-oss-signature-version'] = response.version || 'OSS4-HMAC-SHA256';
          _self.dataObj['x-oss-credential'] = response.x_oss_credential || '';
          _self.dataObj['x-oss-date'] = response.x_oss_date || '';
          _self.dataObj['success_action_status'] = '200';
          
            resolve(true);
          })
          .catch(err => {
            console.log("出错了...",err)
            reject(false);
          });
      });
    },
    handleUploadSuccess(res, file) {
      this.fileList.push({
        name: file.name,
        // url: this.dataObj.host + "/" + this.dataObj.dir + "/" + file.name； 替换${filename}为真正的文件名
        url: this.dataObj.host + "/" + this.dataObj.key.replace("${filename}",file.name)
      });
      this.emitInput(this.fileList);
    },
    handleExceed(files, fileList) {
      this.$message({
        message: "最多只能上传" + this.maxCount + "张图片",
        type: "warning",
        duration: 1000
      });
    }
  }
};
</script>
<style>
</style> -->
<template> 
  <div>
    <el-upload
      :action="dataObj.host"
      :data="dataObj"
      list-type="picture-card"
      :multiple="true" 
      :show-file-list="true"
      :file-list="fileList"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :on-preview="handlePreview">
      <i class="el-icon-plus"></i>
    </el-upload>
    <el-dialog v-model:visible="dialogVisible">
      <img width="100%" :src="previewUrl" alt="">
    </el-dialog>
  </div>
</template>

<script>
import {policy} from './policy'
import { useUserStore } from '@/store/user'
const userStore = useUserStore()
const userId = userStore.userInfo?.id || 'guest'
export default {
  name: 'multiUpload',
  props: {
    modelValue: Array
  },
  data() {
    return {
      dataObj: {
        key: '',
        host: '',
        policy: '',
        'x-oss-signature': '',
        'x-oss-signature-version': '',
        'x-oss-credential': '',
        'x-oss-date': '',
        'success_action_status': ''
      },
      dialogVisible: false,
      previewUrl: '',
      fileList: [], // 用于存储已上传的文件列表
      currentUploadIndex: 0, // 当前上传文件的索引
      pendingFiles: [] // 等待上传的文件列表
    };
  },
  watch: {
    modelValue: {
      handler(newVal) {
        if (newVal && Array.isArray(newVal)) {
          this.fileList = newVal.map(url => ({
            name: url.substring(url.lastIndexOf('/') + 1),
            url: url
          }));
        } else {
          this.fileList = [];
        }
      },
      immediate: true
    }
  },
  methods: {
    emitInput(val) {
      this.$emit('update:modelValue', val)
    },
    handleRemove(file, fileList) {
      // 从文件列表中移除对应文件
      const urls = fileList.map(item => item.url);
      this.emitInput(urls);
    },
    handlePreview(file) {
      this.previewUrl = file.url;
      this.dialogVisible = true;
    },
    handleUploadError(err, file, fileList) {
      console.error('上传失败:', err);
      this.$message.error('文件上传失败');
    },
    beforeUpload(file) {
      this.pendingFiles.push(file);
      // 获取上传策略
      return this.getPolicyForFile(file);
    },
    getPolicyForFile(file) {
      return new Promise((resolve, reject) => {
        policy().then(response => {
          console.log('后端响应:', response);
          
          // 清空所有字段
          Object.keys(this.dataObj).forEach(key => {
            this.dataObj[key] = '';
          });
          
          // 设置OSS V4签名所需字段
          this.dataObj.host = response.host || '';
          this.dataObj.policy = response.policy || '';
          this.dataObj['x-oss-signature'] = response.signature || '';
          this.dataObj['x-oss-signature-version'] = response.version || 'OSS4-HMAC-SHA256';
          this.dataObj['x-oss-credential'] = response.x_oss_credential || '';
          this.dataObj['x-oss-date'] = response.x_oss_date || '';
          this.dataObj['success_action_status'] = '200';
          
          // 设置文件key，确保以dir开头并添加唯一标识
          const dir = response.dir || 'dir';
          this.dataObj.key = dir + '/' + userId + '_' + file.name;
          
          console.log('上传参数:', this.dataObj);
          resolve(true);
        }).catch(err => {
          console.error("获取签名失败:", err);
          this.$message.error('获取上传凭证失败');
          reject(false);
        });
      });
    },
    handleUploadSuccess(res, file, fileList) {
      console.log("上传成功...", res);
      
      // 构建文件访问URL
      const fileUrl = this.dataObj.host + '/' + this.dataObj.key;
      
      // 更新文件列表中的URL
      const targetFile = fileList.find(item => item.uid === file.uid);
      if (targetFile) {
        targetFile.url = fileUrl;
      }
      
      // 发出更新事件
      const urls = fileList.map(item => item.url);
      this.emitInput(urls);
    }
  }
}
</script>

<style scoped>
.el-upload--picture-card {
  width: 148px;
  height: 148px;
}
</style>

