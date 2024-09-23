# 개요

- StateFlow 와 SharedFlow 에 대해 알아보자

# 비교

|                     |                   StateFlow                    |                                                                       SharedFlow                                                                        |
| :-----------------: | :--------------------------------------------: | :-----------------------------------------------------------------------------------------------------------------------------------------------------: |
|        정의         |      최신의 State 값 변화를 위한 Flow API      |                                               여러 콜렉터들에게 Flow 데이터를 전달해 줄 수 있는 Flow API                                                |
|     초기값 필요     |             필요(언제나 값을 가짐)             |                                                                         불필요                                                                          |
|  collect 하는 시점  | collect 하는 시점의 최신값과 후속 상태 값 수신 |                                                         collect 이후 방출되는 후속 값부터 수신                                                          |
| 한번에 수신 가능 값 |              가장 최신의 상태 값               |                                                              일반적인 flow 와 같이 여러 값                                                              |
|        옵션         |                                                | buffer(collector 가 준비가 수신 가능한 상태가 아닐 경우 버퍼크기 설정), <br>replayCache(collect 시 전달받을 이전 데이터의 개수, 몇개까지 캐싱할지) 존재 |
|        용도         | flow 의 state-holder 로 state 변화가 중요한 곳 |                              StateFlow 와 달리 값의 변화 상관없이 flow 데이터를 보낼 수 있어서 이벤트 핸들링에도 사용 가능                              |

- 공통
  - 둘다 hot stream 으로 collect 시점부터 수집합니다.
- StateFlow
  - SharedFlow 의 한종류로 추가로 기본값을 가지고 있습니다.
- SharedFlow
  - replay = 0
    - 새로운 구독자에게 이전 이벤트를 전달하지 않습니다.
    - 0->1->2->3-> collect 시작->4->5->6->7->8->9
      - replay = 0 일 때: 4부터 수신 시작
      - replay = 1 일때: 3부터 수신 시작
      - replay = 4 이상 일때: 0부터 수신 시작
  - extraBufferCapacity = 1
    - buffer 의 개수를 지정합니다.
    - emit 은 buffer 공간이 남아 있는 동안 suspend 되지 않습니다.
    - 버퍼사이즈: replay + extraBufferCapacity
  - onBufferOverflow = BufferOverflow.DROP_OLDEST
    - buffer 가 가득찼을 때 수행할 옵션을 지정합니다.
      - BufferOverflow.SUSPEND: emit이 blocking되며, buffer의 빈 공간이 생겨야 진행 됩니다.
      - BufferOverflow.DROP_OLDEST: 버퍼가 가득찼을 시 오래된 값을 삭제하고, 새로운 값을 넣습니다.
      - BufferOverflow.DROP_LASTEST: 최근 값을 삭제하고, 새로운 값을 넣습니다.
- stateIn, shareIn

  - 하나의 flow 에서 방출된 값을 여러개의 collector 에서 받아야 할 경우 유용합니다.

- StateFlow 와 LiveData

|               |                    StateFlow                     |                        LiveData                        |
| :-----------: | :----------------------------------------------: | :----------------------------------------------------: |
|     정의      |          관찰 가능한 데이터 홀더 클래스          |                          동일                          |
|  초기값 필요  |                       필요                       |                         불필요                         |
| 수명주기 인식 | 자동 인식 불가(Lifecycle.repeatOnLifecycle 사용) | LiveData.observe() 가 전달 받은 lifecycleOwner 로 인식 |

- LiveData 만 쓰지 않는 이유
  - LiveData 는 잘 동작하지만 안드로이드 플랫폼에 독립적이고, 순수 Kotlin 및 java만 사용할 수 있는 즉 언어 의존성만 지니는 Domain layer 에서는 LiveData 를 쓰기 어렵습니다.
  - 또한 계층별로 모듈화를 진행하고 있거나 진행할 예정이라면, LiveData 만을 위해 안드로이드 의존성을 지니게 될 수도 있습니다.
