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
  - 둘다 hot stream 으로 collect 시점부터 수집합니다. [\[출처\]](https://yang-droid.tistory.com/59)
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
- collect, collectLatest 차이
  - collect 는 1~10까지 순차적으로 발행된다고 했을때
  - 소비에 시간이 오래걸리게 될 경우,
  - 소비중인 아이템보다 최신인 아이템 10이 발행되더라도 이전에 발행된 아이템 소비와 UI 업데이트 작업으로 아이템 10의 소비가 발행보다 한참 늦게 되고,
  - 이전 아이템 소비가 안될 경우 그 이후 발행됐던 아이템은 소비가 안되게 됩니다.
  - collectLatest 는 위 collect 의 문제처리를 위해 최신 아이템이 발행됐을 때 이전 데이터 소비를 취소하고 새로 발행된 아이템을 수행하도록 합니다.
  - collectLatest 의 한계
    - 아이템 발행에 0.1초, 소비에 1초가 걸린다면 발행대비 소비가 늦어 모두 취소되고 가장 마지막 데이터만 소비되게 됩니다.
    - 의도한것이 아니라면 주의할 필요가 있습니다.
    - 이를 해결하는 방법으로
      - 한번 시작된 데이터 소비는 끝날 때까지 하고 데이터 소비가 끝난 시점에서의 가장 최신 데이터를 다시 소비하는 방법이 있습니다.
        - conflate 를 이용해 collect 를 하게 되면 시작한 소비는 취소되지 않고 소비가 완료 됐을때 가장 최신의 데이터가 다시 소비하도록 하는 방법입니다.
        - 소비가 오래 걸려도 `consume` 완료 시점에 계속해서 최신의 데이터를 발행받아 소비할 수 있습니다.

```bash
number >> emit 1
number >> emit 2
number >> consume 1
number >> emit 3
number >> emit 4
number >> consume 2
number >> emit 5
number >> consume 4
number >> emit 6
number >> emit 7
number >> consume 5
number >> emit 8
number >> emit 9
number >> consume 7
number >> emit 10
number >> consume 9
number >> consume 10
```

- StateFlow 와 SharedFlow 화면(가로세로) 전환시 값이 어떻게 변하는지?

- StateFlow

```bash
// 1. Activity 세로모드로 생성
onCreate
onStart
// 2. StateFlow 초기값 observe, in onCreate::repeatOnLifecycle(Lifecycle.State.STARTED)
count: 0
onResume
// 3. StateFlow 값 변경
count: 1
count: 2
count: 3
// 4. Activity 가로모드로 변경
onPause
onStop
onDestroy
onCreate
onStart
// 5. StateFlow 현재값 observe
count: 3
onResume
// 6. StateFlow 값 변경
count: 4
```

- SharedFlow

```bash
// 1. Activity 세로모드로 생성
onCreate
onStart
onResume
// 2. SharedFlow observe, in onCreate::repeatOnLifecycle(Lifecycle.State.STARTED)
count: 1
count: 2
count: 3
// 3. Activity 가로모드로 변경
onPause
onStop
onDestroy
onCreate
onStart
onResume
count: 4
count: 5
```

- StateFlow 와 LiveData

|               |                    StateFlow                     |                        LiveData                        |
| :-----------: | :----------------------------------------------: | :----------------------------------------------------: |
|     정의      |          관찰 가능한 데이터 홀더 클래스          |                          동일                          |
|  초기값 필요  |                       필요                       |                         불필요                         |
| 수명주기 인식 | 자동 인식 불가(Lifecycle.repeatOnLifecycle 사용) | LiveData.observe() 가 전달 받은 lifecycleOwner 로 인식 |

- LiveData 만 쓰지 않는 이유

  - LiveData 는 잘 동작하지만 안드로이드 플랫폼에 독립적이고, 순수 Kotlin 및 java만 사용할 수 있는 즉 언어 의존성만 지니는 Domain layer 에서는 LiveData 를 쓰기 어렵습니다.
  - 또한 계층별로 모듈화를 진행하고 있거나 진행할 예정이라면, LiveData 만을 위해 안드로이드 의존성을 지니게 될 수도 있습니다.

- Channel
  - Coroutine 간에 데이터를 주고 받기 위해 만들어진 인터페이스 입니다.
    - send, receive 둘다 suspend 함수입니다.
  - 한쪽에서 값을 send 하면 다른쪽에서 receive 하는 개념입니다.
    - channel.close() 후에 다시 send, receive 할 수 없습니다.
  - 버퍼 정책
    - capacity
      - RENDEZVOUS: Buffer 가 존재하지 않는 정책입니다.
      - UNLIMITED: 버퍼가 무한한 정책입니다.
      - BUFFERED: 크기가 고정된 buffer 를 사용하는 정책입니다.
      - CONFLATED: buffer 가 1인 channel 를 유지하지만, 이미 생산된 데이터가 있는 경우 데이터를 덮어씁니다.
    - onBufferOverflow: Buffer 가 넘치게 되는 경우 취할 수 있는 행동은 크게 3가지 종류
      - SUSPEND: 더 이상 데이터를 Channel 에 받아들이지 않고 대기합니다. (default)
      - DROP_OLDEST: 가장 오래된 데이터를 제거하고 새로운 데이터를 받아들입니다. (queue)
      - DROP_LATEST: 가장 최신의 데이터를 제거하고 새로운 데이터를 받아들입니다. (stack)

|                          |                  Channel                   |                Flow                 |
| :----------------------: | :----------------------------------------: | :---------------------------------: |
|     데이터 생성 위치     |      외부에서 channel.send() 로 생성       |       flow 함수 내부에서 생성       |
|    collect 되는 시점     |      send() 후 receive() 시 배출받음       | collect 시 마다 데이터 생성 후 배출 |
| 동일값을 여러곳에서 소비 | 한번 배출 후 다른곳에서 동일값 소비 불가능 |     여러곳에서 동일값 소비 가능     |
|           버퍼           |               버퍼 정책 존재               |            buffer() 이용            |
|           옵션           |              close 함수 존재               |

- flow
  - buffer()
    - capacity: buffer 의 size 를 몇으로 지정할 것인지 설정
      - default: 64
    - onBufferOverflow: Buffer 가 넘치게 되는 경우 취할 수 있는 행동은 크게 3가지 종류
      - SUSPEND: 일시중지 (default)
      - DROP_OLDEST: 오래된 데이터 버림
      - DROP_LATEST: 가장 최근의 데이터를 최신 데이터로 교체
