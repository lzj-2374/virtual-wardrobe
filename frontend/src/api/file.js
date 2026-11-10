import request from '@/utils/request'

// 上传图片，返回原图与缩略图地址
export async function uploadFile(file, onProgress) {
  const form = new FormData()
  form.append('file', file)
  const res = await request({
    url: '/file/upload',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress
  })
  return res.data
}
