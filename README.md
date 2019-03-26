## Описание проекта

Проект представляет собой сервлет,
который принимает запросы по адресу  
```  
http://127.0.0.1:8080/upload
```
Сервлет обратывает загрузку файла , проверку файла на наличие угроз 

и отправку прошедшего проверку файла конечным получателям.


### Перечень настроек 
Настройки проекта хранятся в файлах *.conf


#### Общие настройки
|Настройка|Значение|Пример|
|---|---|---|
|uploadDir|Директория, в которой будут создавать временные директории для хранения временных файлов. (При загрузке каждого файла внутри uploadDir создаётся новая временная директория с уникальным именем, в эту директорию загружается временный файл, предназначенный для проверки антивирусной системой) |"/tmp/upload"|

#### Настройки Genesys API
|Настройка|Значение|Пример|
|---|---|---|
|genesysApi.baseUrl|Базовый URL для взаимодействия с Chat API Version 2 with CometD|"http://gen01:8090/"|
|customNotice.message|Сообщение отправляемое в чат через Chat API Version 2 with CometD. Сообщение отправляется после загрузки файла во временный каталог, но до проверки файла антивирусной системой и до отправки проверенного файла в чат конечному получателю|"Please wait, while file is loading"|

#### Настройки Threat Prevention API
Запросы к этому API выполняются с использованием такого шаблона
``` 
https://<serverAddress>/tecloud/api/<apiVersion>/file/<operation>
```
Значения для подстановки в шаблон берутся из конфигурационных файлов

|Настройка|Значение|Пример|
|---|---|---|
|teApi.serverAddress|Адрес сервиса (зависит развёрнутого окружения)||
|teApi.serverPort|Порт сервиса (зависит развёрнутого окружения) используется только для Threat Prevention API on a local gateway||
|teApi.apiVersion|Версия Threat Prevention API||
|teApi.apiKey|Валидный __API Key__, который передаётся в качестве значения HTTP-заголовка Authorization при запросах к Threat Prevention API|Authorization: YWJjZDEyMzQ|


### Сценарий обработки запроса на загрузку файла

#### Получение всех необходимых данных из запроса на загрузку файла
* Создается временная директория
* Во временную директорию загружается присланный файл
* Из тела запроса вычитываются обязательные поля: secureKey, clientId 

__secureKey__

Пример значения:
```
NSgiKiYfAV4XDRMzYCVtP15EQggHEH8rZGknVBNBQVBKI3UyanReFw9EVTZ0eBBodlgxS0JVQn4LL29zXEFEFSBFcB4QGnNcQDI=
```
__clientId__

Пример значения:
```
c1vnh0lk2ok4kz53d3kmp1o189
```

__Также запрос должен содержать установленный заголовок cookie__

 
 ```
cookie: BAYEUX_BROWSER=pb8i0p46pvqq214k"
```

Этот заголовок извлекается из запроса и используется в дальнейшем для формирования запросов с Genesys API

#### Отправка запроса Сustom Notice к Genesys Chat API  

Следующим шагом является отправка пользователю чата уведомления о том,
что высланный клиентом файл принят, в настойщий момент проходит проверку 
и будет передан в чат через некоторое время.

Система отправляет запрос к [Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD)

Запрос вызывает операцию __customNotice__

При отправке запроса к Chat API Version 2 with CometD данные clientId, secureKey и заголовок cookie получаются из  запроса на загрузку файла. 

Текст отправляемого сообщения __message__ получаем из настройки __customNotice.message__ конфигурационного файла

Запрос выполняется по следующему URL:
```
http://localhost:8080/genesys/cometd
```

#### Проверка файла при помощи Check Point Threat Prevention API
После успешной отправки customNotice,
происходит проверка безопасности файла при помощи 
[Check Point Threat Prevention API] (http://supportcontent.checkpoint.com/documentation_download?ID=56765)

#### Отправка запроса Genesys Chat API Upload File
Если файл безопасен для пользователей,
то система отправляет файл в чат
при помощи [Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD), 
вызывая операцию Upload File
(operation  fileUpload)

Запрос выполняется по адресу:
```
http://localhost:8080/genesys/2/chat-ntf
```
#### Формирование и отправка ответа на первоначальный POST запрос на загрузку файла

После получения ответа от Genesys Chat API Version 2 with CometD этот ответ копируется и отправляется в качестве ответа клиенту отправившему файла на загрузку по адресу 
http://127.0.0.1:8080/upload

#### Удаление временных директорий и файлов
После этого система удаляет временный каталог вместе с содержащимся в нем временным файлом.

После удаления файлов сценарий обработки запроса на загрузку файла завершен.

  
## Проверка работы системы
Для проверки работы системы, отправьте curl-запрос
такого вида: 
```
curl -v -H "cookie: BAYEUX_BROWSER=pb8i0p46pvqq214k" -F secureKey=NSgiKiYfAV4XDRMzYCVtP15EQggHEH8rZGknVBNBQVBKI3UyanReFw9EVTZ0eBBodlgxS0JVQn4LL29zXEFEFSBFcB4QGnNcQDI= -F clientId=c1vnh0lk2ok4kz53d3kmp1o189 -F upload=@/home/username/test.txt http://127.0.0.1:8080/upload

```
Или Httpie:

```
http -f  POST 127.0.0.1:8080/upload cookie:BAYEUX_BROWSER=son96e  secureKey=NSgiKiYfAV4XDRMzYCVtP15EQghXRncrYWcnDUIRTQQTcn5lOXpVEQ9EVTZ0eBBodShGQ0NVQ3d+L29zXEFLFSBFcB4VDnNcRSo=  clientId=g17gx1cpj4le9v1ua6k2ockma3k  file@/home/username/test.txt
```
## Сборка и запуск

Для запуска проекта локально через sbt :
```
jetty:start или tomcat:start
```
Для остановки:
```
jetty:stop или tomcat:stop
```
Также, если вы хотите перекомпиляции при вненесении вами изменений в код
~~~
 ~jetty:start 
~~~


Для сборки war-файла 
```
sbt package 
```

## Пример ответа на успешную загрузку файла со всеми фичами

```
 {
  "response": {
    "status": {
      "code": 1002,
      "label": "UPLOAD_SUCCESS",
      "message": "The file was uploaded successfully."
    },
    "sha1": "2441842908e39d8c35a1e68120170f978167cf98",
    "md5": "e33a5061ab9e0d85e19636c7ee925f24",
    "sha256": "bbb902202defedd0ce4d53cca3bd16a9eae195e5e4c798e11d8804630375b2e7",
    "file_type": "png",
    "file_name": "Selection_023.png",
    "features": [
      "te",
      "extraction",
      "av"
    ],
    "te": {
      "trust": 0,
      "images": [
        {
          "report": {
            "verdict": "unknown"
          },
          "status": "not_found",
          "id": "e50e99f3-5963-4573-af9e-e3f4750b55e2",
          "revision": 1
        },
        {
          "report": {
            "verdict": "unknown"
          },
          "status": "not_found",
          "id": "5e5de275-a103-4f67-b55b-47532918fa59",
          "revision": 1
        }
      ],
      "score": -2147483648,
      "status": {
        "code": 1002,
        "label": "UPLOAD_SUCCESS",
        "message": "The file was uploaded successfully."
      }
    },
    "extraction": {
      "method": "pdf",
      "tex_product": false,
      "status": {
        "code": 1002,
        "label": "UPLOAD_SUCCESS",
        "message": "The file was uploaded successfully."
      }
    },
    "av": {
      "status": {
        "code": 1002,
        "label": "UPLOAD_SUCCESS",
        "message": "The file was uploaded successfully."
      }
    }
  }
}
```

## Пример запроса на наличие файла и ответа что файл отсутствует

```
{
  "response": {
    "status": {
      "code": 1004,
      "label": "NOT_FOUND",
      "message": "Could not find the requested file. Please upload it."
    },
    "md5": "e33a5061ab9e0d85e19636c7ee925f24",
    "file_type": "png",
    "file_name": "Selection_023.png",
    "features": [
      "extraction"
    ],
    "extraction": {
      "method": "clean",
      "tex_product": false,
      "status": {
        "code": 1004,
        "label": "NOT_FOUND",
        "message": "Could not find the requested file. Please upload it."
      }
    }
  }
}
```

## Пример ответа на запрос Query "Экстракция и конвертация в PDF завершилась успешно"
```
{
  "response": {
    "status": {
      "code": 1001,
      "label": "FOUND",
      "message": "The request has been fully answered."
    },
    "md5": "e33a5061ab9e0d85e19636c7ee925f24",
    "file_type": "png",
    "file_name": "Selection_023.png",
    "features": [
      "extraction"
    ],
    "extraction": {
      "method": "pdf",
      "extract_result": "CP_EXTRACT_RESULT_SUCCESS",
      "extracted_file_download_id": "748a6a1a-62c7-45c3-8935-ca61ddc312a3",
      "output_file_name": "Selection_023.cleaned.png.pdf",
      "time": "0.412",
      "extract_content": "",
      "extraction_data": {
        "input_extension": "png",
        "input_real_extension": "png",
        "message": "OK",
        "output_file_name": "Selection_023.cleaned.png.pdf",
        "protection_name": "Potential malicious content extracted",
        "protection_type": "Conversion to PDF",
        "protocol_version": "1.0",
        "risk": 0.0,
        "scrub_activity": "PNG file was converted to PDF",
        "scrub_method": "Convert to PDF",
        "scrub_result": 0.0,
        "scrub_time": "0.412",
        "scrubbed_content": ""
      },
      "tex_product": false,
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
  }
}
```

## Пример ответа на Thread Extraction запрос (Нет угроз)
```
{
  "response": {
    "status": {
      "code": 1001,
      "label": "FOUND",
      "message": "The request has been fully answered."
    },
    "md5": "e33a5061ab9e0d85e19636c7ee925f24",
    "file_type": "png",
    "file_name": "Selection_023.png",
    "features": [
      "te"
    ],
    "te": {
      "trust": 0,
      "images": [
        {
          "report": {
            "verdict": "benign"
          },
          "status": "found",
          "id": "e50e99f3-5963-4573-af9e-e3f4750b55e2",
          "revision": 1
        },
        {
          "report": {
            "verdict": "benign"
          },
          "status": "found",
          "id": "5e5de275-a103-4f67-b55b-47532918fa59",
          "revision": 1
        }
      ],
      "score": -2147483648,
      "combined_verdict": "benign",
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
  }
}
```
## Пример ответа на Thread Extraction (Обнаружена угроза)
```
{
  "response": {
    "status": {
      "code": 1001,
      "label": "FOUND",
      "message": "The request has been fully answered."
    },
    "md5": "f765caea605d3e42a99e1ccea728787e",
    "file_type": "zip",
    "file_name": "eicar.zip",
    "features": [
      "te"
    ],
    "te": {
      "trust": 10,
      "images": [
        {
          "report": {
            "verdict": "malicious"
          },
          "status": "found",
          "id": "e50e99f3-5963-4573-af9e-e3f4750b55e2",
          "revision": 1
        },
        {
          "report": {
            "verdict": "malicious"
          },
          "status": "found",
          "id": "5e5de275-a103-4f67-b55b-47532918fa59",
          "revision": 1
        }
      ],
      "score": -2147483648,
      "combined_verdict": "malicious",
      "severity": 4,
      "confidence": 3,
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
  }
}
```


## Угроза обнаружена, но не может быть удалена из файла
```
{
  "response": {
    "status": {
      "code": 1001,
      "label": "FOUND",
      "message": "The request has been fully answered."
    },
    "md5": "5f97c5ea28c0401abc093069a50aa1f8",
    "file_type": "xlsx",
    "file_name": "1.xlsx",
    "features": [
      "extraction"
    ],
    "extraction": {
      "method": "clean",
      "extract_result": "CP_EXTRACT_RESULT_NOT_SCRUBBED",
      "output_file_name": "1.xlsx",
      "extraction_data": {
        "input_extension": "xlsx",
        "input_real_extension": "xlsx",
        "message": "Skipped",
        "output_file_name": "",
        "protection_name": "Potential malicious content extracted",
        "protection_type": "Content Removal",
        "protocol_version": "1.0",
        "risk": 0.0,
        "scrub_activity": "The file doesn\u0027t include cleanable parts",
        "scrub_method": "Clean Document",
        "scrub_result": 4.0,
        "scrub_time": "0.093",
        "scrubbed_content": ""
      },
      "tex_product": false,
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
  }
}
```
