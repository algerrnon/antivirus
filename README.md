## Описание проекта

Проект представляет собой сервлет,
который принимает запросы по адресу  
```  
http://127.0.0.1:8080/upload
```
Сервлет обратывает загрузку файла , проверку файла на наличие угроз 

и отправку прошедшего проверку файла пользователям.

### Перечень настроек 
Настройки проекта хранятся в файлах *.conf
 
|Настройка|Значение|Пример|
|---|---|---|
|uploadDir|Директория, в которой будут создавать временные директории для хранения временных файлов. (При загрузке каждого файла внутри uploadDir создаётся новая временная директория с уникальным именем, в эту директорию загружается временный файл, предназначенный для проверки антивирусной системой) |"/tmp/upload"|
|genesysApi.baseUrl|Базовый URL для взаимодействия с Chat API Version 2 with CometD|"http://gen01:8090/"|
|customNotice.message|Сообщение отправляемое в чат через Chat API Version 2 with CometD. Сообщение отправляется после загрузки файла во временный каталог, но до проверки файла антивирусной системой и до отправки проверенного файла в чат конечному получателю|"Please wait, while file is loading"|

### Сценарий обработки запроса на загрузку файла

#### Получение всех необходимых данных из запроса на загрузку файла
* Создается временная директория
* Во временную директорию загружается присланный файл
* Из запроса вычитываются обязательные поля

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


#### Отправка запроса Genesys Chat API Сustom Notice 

Если данные запроса на загрузку файла успешно прочитаны,
система отправляет запрос к [Genesys Chat API Version 2 with CometD](https://docs.genesys.com/Documentation/GMS/8.5.2/API/ChatAPIv2CometD)

Запрос вызывает операцию __customNotice__

При отправке запроса к Chat API Version 2 with CometD данные clientId, secureKey, cookie берутся из запроса по загрузке файла в сервлет.

Текст отправляемого сообщения __message__ берется из конфигурационного файла

Запрос выполняется по адресу:
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
