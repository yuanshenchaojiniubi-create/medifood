<template> 
  <div>
    <el-upload
      :action="dataObj.host"
      :data="dataObj"
      :http-request="customRequest"
      list-type="picture"
      :multiple="false" 
      :show-file-list="showFileList"
      :file-list="fileList"
      :before-upload="beforeUpload"
      :on-remove="handleRemove"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :on-preview="handlePreview">
      <el-button size="small" type="primary">点击上传</el-button>

      <!-- 使用 Vue3 具名插槽语法，替换已弃用的 slot 属性 -->
      <template #tip>
        <div class="el-upload__tip">只能上传jpg/png文件，且不超过10MB</div>
      </template>
    </el-upload>
    <el-dialog v-model:visible="dialogVisible">
      <img width="100%" :src="fileList[0].url" alt="">
    </el-dialog>
  </div>
</template>

<script>
import {policy} from './policy'
import { useUserStore } from '@/store/user'
const userStore = useUserStore()
const userId = userStore.userInfo?.id || 'guest'
export default {
  name: 'singleUpload',
  props: {
    modelValue: String
  },
  computed: {
    imageUrl() {
      return this.modelValue;
    },
    imageName() {
      if (this.modelValue != null && this.modelValue !== '') {
        return this.modelValue.substr(this.modelValue.lastIndexOf("/") + 1);
      } else {
        return null;
      }
    },
    fileList() {
      return [{
        name: this.imageName,
        url: this.imageUrl
      }]
    },
    showFileList: {
      get: function () {
        return this.modelValue !== null && this.modelValue !== ''&& this.modelValue!==undefined;
      },
      set: function (newValue) {
      }
    }
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
      dialogVisible: false
    };
  },
  methods: {
    emitInput(val) {
      this.$emit('update:modelValue', val)
    },
    // 自定义上传，使用原生 XHR 确保所有字段按表单提交到 OSS
    customRequest(option) {
      const file = option.file;
      // 构建 FormData
      const fd = new FormData();
      try {
        Object.keys(this.dataObj).forEach(k => {
          // 只 append 非空字段（OSS 要求某些字段非空）
          fd.append(k, this.dataObj[k] || '');
        });
      } catch (e) {
        console.warn('构建 FormData 时出错', e);
      }
      fd.append('file', file);

      const xhr = new XMLHttpRequest();
      xhr.open('POST', this.dataObj.host, true);
      xhr.withCredentials = false;
      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) {
          const percent = Math.round(e.loaded / e.total * 100);
          option.onProgress({ percent: percent }, file);
        }
      };
      xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          // 返回 XML 或 JSON，直接传给 onSuccess
          option.onSuccess(xhr.responseText, file);
        } else {
          option.onError(new Error(xhr.responseText || 'upload error'));
        }
      };
      xhr.onerror = () => {
        option.onError(new Error('upload error'));
      };
      xhr.send(fd);
    },
    handleRemove(file, fileList) {
      this.emitInput('');
    },
    handlePreview(file) {
      this.dialogVisible = true;
    },
    handleUploadError(err, file, fileList) {
      console.error('上传失败:', err);
      this.$message.error('文件上传失败');
    },
    beforeUpload(file) {
      let _self = this;
      return new Promise((resolve, reject) => {
        policy().then(response => {
          console.log('后端响应:', response);
          
          // 清空所有字段
          Object.keys(_self.dataObj).forEach(key => {
            _self.dataObj[key] = '';
          });
          
          // 设置OSS V4签名所需字段
          _self.dataObj.host = response.host || '';
          _self.dataObj.policy = response.policy || '';
          _self.dataObj['x-oss-signature'] = response.signature || '';
          _self.dataObj['x-oss-signature-version'] = response.version || 'OSS4-HMAC-SHA256';
          _self.dataObj['x-oss-credential'] = response.x_oss_credential || '';
          _self.dataObj['x-oss-date'] = response.x_oss_date || '';
          _self.dataObj['success_action_status'] = '200';
          
          // 设置文件key，确保以dir开头并添加唯一标识
          const dir = response.dir || 'dir';
          _self.dataObj.key = dir + '/' + userId + '_' + file.name;
          
          console.log('上传参数:', _self.dataObj);
          // 确保 dataObj 是新的对象引用，避免 el-upload 在序列化时读取到旧的 proxy/空字段
          _self.dataObj = Object.assign({}, _self.dataObj);

          console.log('最终 dataObj 引用已替换，等待上传：', _self.dataObj);
          resolve(true)
        }).catch(err => {
          console.error("获取签名失败:", err);
          this.$message.error('获取上传凭证失败');
          reject(false)
        })
      })
    },
    handleUploadSuccess(res, file) {
      console.log("上传成功...", res);
      this.showFileList = true;
      this.fileList.pop();
      // 构建文件访问URL
      const fileUrl = this.dataObj.host + '/' + this.dataObj.key;
      this.fileList.push({name: file.name, url: fileUrl});
      this.emitInput(fileUrl);
    }
  }
}
</script>

<style>
</style>