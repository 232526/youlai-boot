# 开放API - 短信接口文档

## 一、鉴权说明

所有接口请求均需在 HTTP Header 中携带以下三个签名参数：

| Header 参数 | 类型   | 必填 | 说明                                           |
|-------------|--------|------|------------------------------------------------|
| X-Api-Key   | String | 是   | 平台分配的 API Key                             |
| X-Timestamp | String | 是   | 当前系统时间戳（**秒级**）                     |
| X-Sign      | String | 是   | 签名字符串（不区分大小写）                     |

### 签名生成规则

```
sign = MD5(apiKey + apiSecret + timestamp)
```

- 将 `API Key`、`API Secret`、`Timestamp` 三个字符串**直接拼接**（无分隔符）
- 对拼接后的字符串进行 **MD5** 加密，生成 **32位** 十六进制字符串
- 签名比对**不区分大小写**
- 时间戳有效期为 **5 分钟**，超出将返回"签名已过期"

### 签名示例

```
API Key:    ak_test123
API Secret: sk_secret456
Timestamp:  1714470000

拼接字符串: ak_test123sk_secret4561714470000
MD5签名:    e10adc3949ba59abbe56e057f20f883e（示例值）
```

---

## 二、接口列表

### 1. 创建短信订单

**请求地址：** `POST /api/v1/open/sms-orders`

**Content-Type：** `application/json`

#### 请求参数（Body JSON）

| 参数            | 类型         | 必填 | 说明                         | 限制                  |
|-----------------|-------------|------|------------------------------|-----------------------|
| content         | String      | 是   | 短信内容                     | 最大长度 1024 个字符  |
| phoneNumberList | String[]    | 是   | 手机号列表                   | 最多 2000 个号码；每个号码最大长度 32 个字符 |

#### 请求示例

```bash
curl -X POST 'https://your-domain.com/api/v1/open/sms-orders' \
  -H 'Content-Type: application/json' \
  -H 'X-Api-Key: ak_test123' \
  -H 'X-Timestamp: 1714470000' \
  -H 'X-Sign: e10adc3949ba59abbe56e057f20f883e' \
  -d '{
    "content": "您的验证码是123456，5分钟内有效。",
    "phoneNumberList": ["8613800138000", "8613900139000"]
  }'
```

#### 成功响应

```json
{
  "code": "00000",
  "msg": "成功",
  "data": "SMS202604300001"
}
```

`data` 返回值为**订单编号**（String），后续可用此编号查询订单状态。

#### 失败响应示例

```json
{
  "code": "B0001",
  "msg": "电话列表不能超过2k"
}
```

---

### 2. 查询短信订单详情

**请求地址：** `GET /api/v1/open/sms-orders/{id}`

#### 路径参数

| 参数 | 类型 | 必填 | 说明   |
|------|------|------|--------|
| id   | Long | 是   | 订单ID |

#### 请求示例

```bash
curl -X GET 'https://your-domain.com/api/v1/open/sms-orders/123' \
  -H 'X-Api-Key: ak_test123' \
  -H 'X-Timestamp: 1714470000' \
  -H 'X-Sign: e10adc3949ba59abbe56e057f20f883e'
```

#### 成功响应

```json
{
  "code": "00000",
  "msg": "成功",
  "data": {
    "id": 123,
    "orderNo": "SMS202604300001",
    "countryId": 1,
    "countryName": "中国",
    "hasAreaCode": 0,
    "scheduledTime": "2026-04-30 15:02:00",
    "messageContentList": ["您的验证码是123456，5分钟内有效。"],
    "phoneNumberList": ["8613800138000", "8613900139000"],
    "status": 2,
    "statusDesc": "已完成",
    "successCount": 2,
    "failCount": 0,
    "totalCount": 2,
    "remark": null,
    "createTime": "2026-04-30 15:00:00"
  }
}
```

#### 响应字段说明

| 字段               | 类型     | 说明                                           |
|--------------------|----------|------------------------------------------------|
| id                 | Long     | 订单ID                                         |
| orderNo            | String   | 订单编号                                       |
| countryId          | Integer  | 国家ID                                         |
| countryName        | String   | 国家名称                                       |
| hasAreaCode        | Integer  | 是否携带区号：0-否，1-是                       |
| scheduledTime      | String   | 预约启动时间（格式：yyyy-MM-dd HH:mm:ss）      |
| messageContentList | String[] | 短信内容列表                                   |
| phoneNumberList    | String[] | 手机号列表                                     |
| status             | Integer  | 订单状态：0-待发送，1-发送中，2-已完成，3-发送失败，4-已取消 |
| statusDesc         | String   | 订单状态描述                                   |
| successCount       | Integer  | 发送成功数量                                   |
| failCount          | Integer  | 发送失败数量                                   |
| totalCount         | Integer  | 总数                                           |
| remark             | String   | 备注                                           |
| createTime         | String   | 创建时间（格式：yyyy-MM-dd HH:mm:ss）          |

---

## 三、公共错误码

| 错误码 | 说明                     | 常见原因                         |
|--------|--------------------------|----------------------------------|
| 00000  | 成功                     | -                                |
| A0301  | 访问未授权               | API Key无效 / 签名错误 / 签名过期 |
| A0202  | 用户账户被冻结           | 账户被禁用或余额不足             |
| A0400  | 用户请求参数错误         | 时间戳格式错误                   |
| B0001  | 系统执行出错             | 业务校验失败或服务端异常         |

---

## 四、各语言签名示例

### Java

```java
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class SignUtil {
    public static String generateSign(String apiKey, String apiSecret, long timestamp) {
        String raw = apiKey + apiSecret + timestamp;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### Python

```python
import hashlib, time

def generate_sign(api_key: str, api_secret: str, timestamp: int) -> str:
    raw = f"{api_key}{api_secret}{timestamp}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()

# 使用示例
ts = int(time.time())
sign = generate_sign("ak_test123", "sk_secret456", ts)
```

### JavaScript / Node.js

```javascript
const crypto = require('crypto');

function generateSign(apiKey, apiSecret, timestamp) {
  const raw = apiKey + apiSecret + timestamp;
  return crypto.createHash('md5').update(raw, 'utf8').digest('hex');
}

// 使用示例
const ts = Math.floor(Date.now() / 1000);
const sign = generateSign('ak_test123', 'sk_secret456', ts);
```

### PHP

```php
function generateSign($apiKey, $apiSecret, $timestamp) {
    return md5($apiKey . $apiSecret . $timestamp);
}

// 使用示例
$ts = time();
$sign = generateSign('ak_test123', 'sk_secret456', $ts);
```
