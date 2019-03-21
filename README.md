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
