![44](http://alt-devdepot.altuera.local:3000/open-ru/gms-antivirus-service/raw/master/src/main/resources/img/antivirus-swimlanes.png)

## Описание проекта

Сервис, принимающий запросы по адресу  
```  
http://[host]:[port(default=8080)]/[имя war-файла проекта без расширения]/upload
```

Перед загрузкой файла в Tomcat или другой контейнер,
вы можете переименовать WAR-файл поставки

После загрузки переименованного файла, его новое имя (без учёта расширения файла) будет использовано сервером Tomcat как часть URL по которому вы сможете посылать HTTP-запросы к сервису. 

Эта часть показана на шаблоне выше как `[имя war-файла проекта без расширения]`
 
------------------
 

Сервис выполняет следующие функции:

* Принимает файл от Клиента и сохраняет его

* Проверяет файл на наличие угроз 

* Конвертирует файл в безопасный для работы формат 

* Удаляет из файла опасные участки 

* Возвращает Клиенту ответ по завершении сценария обработки

* Отправляет сообщение в чат (при помощи Genesys API)

* Отправляет файл в чат (при помощи Genesys Chat API)

* Предоставляет ссылку для скачивания оригиналов файлов и возможность скачивать файлы из хранилища.

 
------------------

В дальнейшем в описании мы будем использовать такие названия для участников взаимодействия (в тексте они будут начинаться с заглавной буквы и будут __выделены__):

* __HTTP-клиент__ - программа отправляющая запросы на загрузку файлов
* __Сервис__ - сервис принимающих запросы на загрузку файлов и обрабатывающий эту загрузку (текущий проект)
* __Chat API__ - Genesys API для взаимодействия с чатами: загрузки сообщений, файлов в чат и т.д.
* __Пользователь__ - пользователь чата, получающий сообщения в чате, скачивающий из файлы
* __Thread Prevention API__ - API для предотвращения угроз, способное выполнять проверку и очистку/конвертацию файлов в безопасные форматы

 
 
------------------

### Используемые API:
|Название|Назначение|Ссылка на документацию|Версия|
|---|---|---|---|
|Thread Prevention API|Конвертация файлов в безопасный формат (PDF) обеспечивается при помощи Thread Prevention API - Thread Extraction, method pdf|[THREAT PREVENTION API 1.0 Reference Guide](http://supportcontent.checkpoint.com/documentation_download?ID=56765)|1.0|
| |Удаление из файлов опасных участков обеспечивается Thread Prevention API - Thread Extraction, method clean|[THREAT PREVENTION API 1.0 Reference Guide](http://supportcontent.checkpoint.com/documentation_download?ID=56765)|1.0|
| |Проверка файлов на начилие угроз происходит при помощи Thread Prevention API - Thread Emulation|[THREAT PREVENTION API 1.0 Reference Guide](http://supportcontent.checkpoint.com/documentation_download?ID=56765)|1.0|
|Genesys Chat API Version 2 with CometD|Взаимодействие с чатами Genesys, отправка сообщений, отправка файлов в чат|[Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD)|8.5.2|

### Сценарий обработки запроса на загрузку файла


#### Получение всех необходимых данных из запроса на загрузку файла
* __Клиент__ отправляет multipart запрос на загрузку файла в чат 

Запрос отправляется по адресу:
```  
http://[host]:[port(default=8080)]/upload
```

* __Система__ создаёт временную директорию с файлом вида __/tmp/upload/1553873076585-0/test.txt__ 
* Во временную директорию загружается присланный файл
* Из тела запроса вычитываются обязательные поля: secureKey, clientId 
и значение заголовка cookie

__secureKey__

Пример:
```
NSgiKiYfAV4XDRMzYCVtP15EQggHEH8rZGknVBNBQVBKI3UyanReFw9EVTZ0eBBodlgxS0JVQn4LL29zXEFEFSBFcB4QGnNcQDI=
```
__clientId__

Пример:
```
c1vnh0lk2ok4kz53d3kmp1o189
```

__заголовок cookie__

Пример:
 
 ```
cookie: BAYEUX_BROWSER=pb8i0p46pvqq214k"
```

Заголовок cookie извлекается из запроса и используется в дальнейшем для формирования запросов с __Chat API__, так как без наличия этого заголовка __Chat API__ будет отклонять запросы. 

#### Отправка запроса Сustom Notice к Genesys Chat API  

Следующим шагом является отправка пользователю чата уведомления о том,
что высланный клиентом файл принят, проходит проверку 
и будет передан в чат через некоторое время.

Система отправляет запрос к [Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD)

Запрос вызывает операцию __customNotice__

При отправке запроса к __Chat API__ Version 2 with CometD данные используются clientId, secureKey и заголовок cookie из предыдущего шага. 

Текст отправляемого сообщения __message__ получаем из настройки __customNotices.pleaseWait__ 

Запрос выполняется по следующему адресу:
```
http://[genesys-host]:[genesys-port]/genesys/cometd
```

#### Проверка файла при помощи Check Point Threat Prevention API
После отправки customNotice,
происходит проверка безопасности файла при помощи 
[Check Point Threat Prevention API](http://supportcontent.checkpoint.com/documentation_download?ID=56765)

При этом, сценарий проверки зависит от типа файла.
Тип файла определяется по наличию расширения файла в соответствующем списке.
Список типов файлов, которые будут подвергнуты очистке от угроз 
содержится в настройке 
__avApi.fileExtensionsLists.forThreadExtraction__

Список типов файлов, которые должны быть конвертированы в PDF 
содержится в настройке
__avApi.fileExtensionsLists.forConvertToPdf__

Список типов файлов для проверки ThreadEmulation содержится в настройке 
__avApi.fileExtensionsLists.forThreadEmulation__

Часть конфигурационного файла с установленными настройками fileExtensionsLists:
```
avApi {
  fileExtensionsLists = {
    forThreadExtraction = ["doc", "docx", "xls", "xlsx", "jpeg", "png"]
    forConvertToPdf = ["doc", "docx", "jpeg", "png"]
    forThreadEmulation = ["doc, docx, xls, xlsx"]
  }
} 
```

Если файл конвертируется в PDF, то он PDF-копия высылается в чат непосредственно.
Для файлов, которые должны пройти проверку Thread Emulation, после ответа от ThreadEmulation о том, что файл безопасен, создаём копию оригинала файла.

Для этого:
* получаем из конфигурационного файла директорию storageBaseDir 
* создаём новую директорию с уникальным именем вида 1553873076585-0 внутри директории storageBaseDir
* копируем оригинал файла в директорию созданную в предыдущем шаге
* в результате каждый файл находится в отдельной директории 

Пользователь чата вместе с уведомлением об успешной проверке получает также ссылку на скачивание оригинала файла.
Ссылка имеет вид 
```
http://[host]:[port(default=8080)]/[имя war-файла проекта без расширения]/getFile?fileId=1553885449363-0
```
Значение параметра __fileId__ - это имя каталога, в который помещена копия оригинального файла

__При этом система определяет наличие вредоносного содержимого только по результатам проверки Thread Emulation (te : te.combined_verdict: "malicious", а данные из av : av.malware_info.signature_name при проверке не используются__

#### Отправка запроса Genesys Chat API Upload File
Если файл безопасен для пользователей,
то система отправляет файл в чат
при помощи [Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD), 
вызывая операцию Upload File
(operation  fileUpload)

Запрос выполняется по адресу:
```
http://[genesys-host]:[genesys-port]/genesys/2/chat-ntf
```
#### Формирование и отправка ответа на первоначальный POST запрос на загрузку файла

После получения ответа от Genesys Chat API Version 2 with CometD этот ответ копируется и отправляется в качестве ответа клиенту отправившему файла на загрузку по адресу 
http://127.0.0.1:8080/upload

Или, если во время сценария произошла ошибка, или загружаемый файл содержал угрозы - __Клиент__ получит сообщение об ошибке или о том что файл содержал угрозы.

__Пользователь__ также получит уведомление о том, что файл содержал угрозы,
если угрозы будут обнаружены

### Перечень настроек 
Настройки проекта хранятся в файле application.conf

#### Общие настройки
|Настройка|Значение|Пример|
|---|---|---|
|uploadBaseDir|Директория, в которой будут создавать временные директории для хранения временных файлов. (При загрузке каждого файла внутри uploadDir создаётся новая временная директория с уникальным именем, в эту директорию загружается временный файл, предназначенный для проверки антивирусной системой) |"/tmp/upload"|
|storageBaseDir|Директория для хранения оригиналов файлов, которые прошли успешную проверку Thread Emulation и могут быть запрошены пользователем чата по ссылке вида http://[host]:[port]/getFile?fileId=[fileId]|"/tmp/storage"|

#### Настройки Genesys API
|Настройка|Значение|Пример|
|---|---|---|
|genesysApi.baseUrl|Базовый URL для взаимодействия с Chat API Version 2 with CometD|"http://gen01:8090/"|

#### Сообщения отправляемые пользователю
|Настройка|Значение|Пример|
|---|---|---|
|customNotice.pleaseWait|Сообщение отправляемое в чат через Chat API Version 2 with CometD. Сообщение отправляется после загрузки файла во временный каталог, но до проверки файла антивирусной системой и до отправки проверенного файла в чат конечному получателю|_В настоящий момент не используется_|
|customNotice.isSafeFileCopy|Вам будет отправлен безопасный файл|"Вам выслан файл. Антивирусная система предоставляет вам безопасную копию файла"|
|customNotice.isSafeFileAndLinkToOriginal|Вам будет отправлен безопасный файл. Также в сообщении будет ссылка, при помощи которой вы можете запросить оригинальный файл|"Проверка оригинала файла на вирусы завершена. Файл безопасен, вы можете скачать его по ссылке: "|
|customNotice.isInfectedFile|Этот файл опасен|"Проверка оригинала файла на вирусы завершена. Внимание! Оригинал файла содержит потенциально опасное содержимое и не будет доступен для скачивания"|
|customNotice.isCorruptedFile|Это файл поврежден, зашифрован или содержит ошибки|_В настоящий момент не используется_|
|customNotice.fileNotFound|Файл отсутствует и не может быть получен: некорректный ответ антивирусной системы, неподдерживаемый тип файла и подобные ситуации|"Файл отсутствует или не может быть получен"|

#### Настройки Threat Prevention API
Запросы к Thread Prevention API выполняются с использованием такого шаблона
``` 
https://[serverAddress]/tecloud/api/[apiVersion]/file/<operation>
```
Значения для подстановки в шаблон берутся из application.conf

|Настройка|Значение|Пример|
|---|---|---|
|avApi.serverAddress|Адрес сервиса (зависит развёрнутого окружения)|"te.checkpoint.com" - для Cloud|
|avApi.serverPort|Порт сервиса (зависит развёрнутого окружения) используется только для Threat Prevention API on a local gateway||
|avApi.apiVersion|Версия Threat Prevention API|текущая версия "v1"|
|avApi.apiKey|Валидный __API Key__, который передаётся в качестве значения HTTP-заголовка Authorization при запросах к Threat Prevention API|Пример заголовка Authorization: YWJjZDEyMzQ|
|avApi.retry.maximumWaitTimeSeconds|Максимальное время ожидания результата от метода, который выполняет периодические попытки получения ответа о результате той или иной операции (в текущей системе это методы взаимодействия с от Thread Prevention API, так как Thread Prevention API при нагрузках может требовать выполнения нескольких повторяющихся запросов до получения ответа содержащего результат)|120 (120 секунд)
|avApi.retry.pauseBetweenAttemptsMilliseconds|Паузы между попытками|500 (500 миллисекунд)|
|avApi.retry.maxNumberOfTimes|Число попыток после достижения которого попытки будут прекращены (если число = 3, значит будет выполнено 4 попытки)|30|
|avApi.proxyHost|Адрес прокси, которое может использоваться для доступа к серверу антивирусной системы|в настоящий момент не используется|
|avApi.proxyPort|Порт прокси, которое может использоваться для доступа к серверу антивирусной системы|в настоящий момент не используется|
|avApi.vailableOsImages|Список образов ОС, которые используются антивирусной системой для проверки безопасности файлов в Thread Emulation. При запросе проверки безопасности файла в ответе можно получить общую информацию, а также информацию для каждого из образов ОС|в настоящий момент не используется|
|avApi.fileExtensionsLists|Списки, содержащие поддерживаемые антивирусной системой (Checkpoint Security Gateway)  типы файлов (doc, png, xlsx и другие), с разделением на предопределённые группы (группы перечислены в таблице ниже)|||
|avApi.fileExtensionsLists.forThreadExtraction|__forThreadExtraction__ - файлы имеющие указанные расширения будут обрабатываться при помощи процедуры Thread Extraction. Если вы предполагаете использование Thread Extraction для превентивного удаления угроз из файлов, то следует установить в настройке forThreadExtraction перечень типов файлов (расширение файла без точки) к которым вы хотите применять Thread Extraction (при условии, что Threat Extraction blade поддерживает работу со всеми этими типами файлов)|Пример конфига|
|avApi.fileExtensionsLists.forConvertToPdf|__forConvertToPdf__ - файлы имеющие указанные расширения будут конвертироваться в PDF при обработке Thread Extraction. Настройка forConvertToPdf определяет __подмножество__ ранее указанных в настройке forThreadExtraction расширений. Приведенные в forConvertToPdf будут конвертироваться в PDF, не приведенные в forConvertToPdf, но указанные forThreadExtraction расширения будут очищаться от угроз без конвертации в PDF, то есть с сохранением исходного формата файла.||
|avApi.fileExtensionsLists.forThreadEmulation|__forThreadEmulation__ - настройка которая определяет какие из файлов будут проходить проверку Thread Emulation. При этом данная настройка может содержать типы файлов, уже указанные в настройке forThreadExtraction, в таком случае, указанные типы файлов будут обработаны Thread Extraction (после этого получателю будет выслана безопасная копия), а затем будут обработаны Thread Emulation (после этого получателю будет выслан оригинал, если он безопасен или будет отправлено предупреждение о невозможности доставить файл из-за содержащихся в нём угроз)||
 


#### Удаление временных директорий и файлов
После этого система отмечает временный каталог вместе с содержащимся в нем временным файлом,
как предназначенные для удаления.

Файлы или директории отмеченные таким образом будут удалены при завершении работы
виртуальной машины Java.

Файлы и директории удаляются в обратном порядке их регистрации.

Повторная попытка вызвать метод удаления для уже зарегистированного файла или директории
не имеет никакого эффекта.

Удаление будет проводиться только при корректном завершении работы виртуальной машины Java.

После запроса на удаление невозможно отменить запрос.

__После установки признака удаления файлов сценарий обработки запроса на загрузку файла завершен.__

Файлы из директории uploadBaseDir подвергаются удалению.

Файлы из директории storageBaseDir в настоящим момент не удаляются.
  
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
## Пример ответа в случае обнаружения вируса
```
    "av": {
      "malware_info": {
        "signature_name": "Trojan.Win32.Generic.TC.iciffcdigcf",
        "malware_family": 308791,
        "malware_type": 114,
        "severity": 4,
        "confidence": 5
      },
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
```

## Пример ответа, где вирус не обнаружен

```
    "av": {
      "malware_info": {
        "signature_name": "",
        "malware_family": 0,
        "malware_type": 0,
        "severity": 0,
        "confidence": 0
      },
      "status": {
        "code": 1001,
        "label": "FOUND",
        "message": "The request has been fully answered."
      }
    }
```
