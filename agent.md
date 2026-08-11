# Android AI Coding Guide

> **Bắt buộc:** Mọi AI Coding Assistant (Cursor, Claude Code, ChatGPT) phải đọc và tuân thủ tài liệu này trước khi sửa mã. Nếu yêu cầu mâu thuẫn với tài liệu, hãy nêu rõ mâu thuẫn và hỏi lại thay vì tự ý phá vỡ kiến trúc.

## 0. Quy trình bắt buộc: Plan trước khi làm

Trước mọi thay đổi không tầm thường, AI **PHẢI**:

1. Đọc các file liên quan và kiểm tra cấu trúc hiện có.
2. Trình bày plan ngắn: mục tiêu, file sẽ sửa/tạo, luồng dữ liệu và rủi ro.
3. Chờ xác nhận nếu thay đổi ảnh hưởng API, database schema, navigation, dependency, hoặc gồm hơn 3 file. Với sửa lỗi nhỏ/rõ ràng, có thể thực hiện ngay nhưng vẫn phải nêu plan một dòng.
4. Implement theo plan, chạy test/Build phù hợp, rồi báo cáo file đã đổi và kết quả xác minh.

Không tạo code giả định khi chưa biết contract, model, hoặc yêu cầu nghiệp vụ.

## 1. Overview & Tech Stack

### Stack chuẩn

| Hạng mục | Quy ước bắt buộc |
| --- | --- |
| Language | Kotlin (không viết code production mới bằng Java) |
| UI | Jetpack Compose + Material 3; Compose for TV ở module TV |
| Architecture | MVVM + Clean Architecture, ưu tiên feature-first |
| DI | Hilt |
| Local database | Room |
| Async/reactive | Kotlin Coroutines + Flow/StateFlow |
| Networking LAN | WebSocket; TV là authoritative host, controller là client |
| Navigation | Navigation Compose |
| Test | JUnit, Turbine cho Flow, Compose UI Test |

### Dependency/version policy

- Quản lý version tập trung bằng `gradle/libs.versions.toml`; không hard-code version ở module build file.
- Dùng stable release tương thích với `compileSdk` hiện tại. Không tự nâng AGP/Kotlin/Compose BOM nếu không được yêu cầu.
- Ưu tiên: Kotlin **2.x**, Coroutines **1.8+**, Compose BOM **2025.x+**, Lifecycle **2.8+**, Hilt **2.5x+**, Room **2.6+**, Navigation Compose **2.8+**. Phiên bản thực tế phải được xác minh qua version catalog/repository trước khi thêm.
- Compose dùng BOM; không gán version riêng cho từng thư viện Compose.
- Chỉ thêm dependency khi cần; dùng KSP cho Room/Hilt, không thêm KAPT mới trừ khi dependency bắt buộc.

## 2. Project Directory Structure Map

Dự án karaoke là multi-module và hỗ trợ role runtime. `tvApp` luôn chạy **Host**; `mobileApp` chạy trên phone/tablet, mặc định là **Controller** nhưng tablet có thể chọn **Host**. Host sở hữu playback/queue; Controller chỉ điều khiển qua LAN. Không để Controller truy cập trực tiếp database hoặc YouTube player của Host.

```text
core/
├── common/                  # Result, dispatcher, extension, error model
├── model/                   # Song, QueueItem, PlaybackState, ScoreSnapshot
├── protocol/                # WebSocket message/command contract (pure Kotlin)
├── database/                # Room entity, DAO, database, migration
├── network/                 # WebSocket server/client abstractions
└── ui/                      # theme, shared Compose component (không phụ thuộc TV)
host/
└── src/main/java/com/ntech/nkara/host/
    ├── data/                # repository implementation, Room mapping
    ├── domain/              # repository interface, use case
    ├── host/                # WebSocket server, session, command dispatcher
    ├── playback/            # YouTube player adapter, playback coordinator
    ├── scoring/             # host scoring persistence/display adapter
    └── di/                  # Host Hilt modules
controller/
└── src/main/java/com/ntech/nkara/controller/
    ├── connection/          # QR/IP connection, WebSocket client, discovery
    ├── data/                # remote repository implementation
    ├── domain/              # remote repository interface, use case
    ├── scoring/             # AudioRecord from phone/tablet mic, score sender
    ├── presentation/        # controller screen, ViewModel, UI state/event/effect
    └── di/                  # controller Hilt modules
tvApp/
└── src/main/java/com/ntech/nkara/tv/
    └── presentation/        # TV Host screen, TV navigation and ViewModel
mobileApp/
└── src/main/java/com/ntech/nkara/mobile/
    ├── role/                # Role selector, active-role session coordinator
    └── presentation/        # phone/tablet Host + Controller routes
```

Phone/tablet dùng `mobileApp`. Tablet dùng responsive Compose: màn hình rộng hiển thị hai pane (thêm bài + queue); màn hình hẹp hiển thị một pane có navigation. Mở app lần đầu và trong Settings phải có role selector rõ ràng: **Host** hoặc **Controller**. Phone cũng có thể chọn Host nếu thiết bị/phục vụ phù hợp, nhưng UI phải cảnh báo trải nghiệm playback tốt nhất trên tablet/TV.

Không để `Entity`, `Dao`, WebSocket DTO, Android `Context`, hay resource ID xuất hiện trong `domain`.

## 3. Architecture Guidelines

### Dependency direction

```text
TV: Compose Screen -> ViewModel -> UseCase -> Repository interface (domain)
                                         <- Repository implementation (data) -> Room/YouTube player
Phone/tablet: Compose Screen -> ViewModel -> UseCase -> Remote repository interface
                                                    <- WebSocket client -> TV host
```

- `presentation`: chỉ render state và chuyển user action thành event. `ViewModel` giữ UI state, gọi use case; không gọi DAO/repository implementation trực tiếp.
- `domain`: Kotlin thuần, độc lập Android framework. Use case có một trách nhiệm, `operator fun invoke(...)` khi phù hợp.
- `data`: triển khai repository; mapping ở boundary; Room `Entity` không được trả ra UI/domain.
- Repository read trả `Flow<DomainModel>` khi dữ liệu có thể quan sát; write là `suspend` và trả `Result`/domain error rõ ràng.
- Hilt binding đặt ở `di` hoặc `data/di`; inject abstraction (`Repository`), không inject concrete class vào ViewModel.
- Room migration là bắt buộc khi schema production thay đổi; không dùng destructive migration trừ khi được phê duyệt rõ ràng.
- Host đang active là **single source of truth**: Host giữ queue, playback state, favorites và history trong Room. Controller gửi command; Host validate, apply rồi broadcast snapshot mới. Host có thể là TV hoặc tablet/phone đã chọn role Host.
- Giao thức WebSocket phải có `protocolVersion`, `requestId`, command type, payload rõ ràng và response error. Không gửi Java/Kotlin object serialization mặc định qua network.
- Kết nối Controller dùng QR chứa `ws://<host-ip>:<port>` và nhập IP thủ công. Chỉ hỗ trợ LAN; không expose port ra Internet.
- Chấm điểm mặc định dùng mic phone/tablet: Controller gửi `ScoreSnapshot` theo interval; Host hiển thị/lưu điểm cuối. TV microphone là optional capability, không được giả định luôn tồn tại.
- Khi đổi role trên mobile/tablet, phải disconnect WebSocket client, dừng và đóng local host server (nếu có), rồi mới khởi tạo role mới. Một thiết bị không được đồng thời là Host và Controller trong cùng phiên.
- Khi Controller kết nối Host mới, xóa snapshot queue cũ, đồng bộ snapshot từ Host trước khi cho thao tác queue.

## 4. Coding Standards & Conventions

### Naming & file rules

- Class/file: `PascalCase`: `TaskListViewModel.kt`, `ObserveTasksUseCase.kt`.
- Function/variable: `camelCase`; constant: `UPPER_SNAKE_CASE`.
- UI: `<Feature>Screen`, reusable component: `<MeaningfulName>Content`; state: `<Feature>UiState`; event: `<Feature>UiEvent`.
- Boolean bắt đầu bằng `is`, `has`, `can`, `should`. Không dùng tên mơ hồ như `data`, `result`, `manager`, `utils`.
- Một public top-level class/interface chính mỗi file. Không dùng wildcard import.

### StateFlow & Compose

- `UiState` là immutable `data class`, có giá trị mặc định và biểu diễn đầy đủ `isLoading`, data, lỗi có thể hiển thị.
- ViewModel giữ `_uiState = MutableStateFlow(...)` là `private`; chỉ expose `val uiState: StateFlow<...> = _uiState.asStateFlow()`.
- UI collect bằng `collectAsStateWithLifecycle()`; không collect Flow trực tiếp trong composable body.
- Event từ UI đi qua **một** `onEvent(event)`; không truyền ViewModel xuống composable con khi có thể truyền state + callback.
- Side effect một lần (navigation, snackbar) dùng `UiEffect` + `SharedFlow`/`Channel`, không nhét vào persistent `UiState`.

### Coroutines, Flow & error handling

- Chỉ launch từ lifecycle-aware scope: `viewModelScope` trong ViewModel, `LaunchedEffect` trong Compose. Không dùng `GlobalScope`.
- Không bọc toàn bộ Flow bằng `try/catch`; dùng `.catch {}` trước `stateIn`/`collect`, và không nuốt exception.
- Không gọi blocking I/O trên Main. Repository/data source chịu trách nhiệm dispatcher I/O nếu thao tác blocking.
- Không expose `Throwable.message` thẳng ra UI. Map lỗi sang domain/UI message có thể hành động; log exception với context, không log secret/PII.
- Ưu tiên `Result<T>` (hoặc sealed `AppResult`) cho thao tác có thể thất bại. `CancellationException` phải được rethrow.
- Sử dụng `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue)` cho state bắt nguồn từ Flow.

## 5. Rules & Prohibitions

### CẤM

- CẤM gọi DAO, Room database, network client hoặc `Context` từ Composable/ViewModel/domain use case.
- CẤM để UI phụ thuộc `Entity`/DTO, hoặc để data layer phụ thuộc presentation layer.
- CẤM `GlobalScope`, `runBlocking` trong production, callback lồng nhau, `Thread.sleep`, hoặc tự tạo `CoroutineScope` không quản lý lifecycle.
- CẤM mutable collection trong `UiState`, `MutableStateFlow` public, hoặc sửa trực tiếp state đang expose.
- CẤM dùng `LiveData` cho code mới khi Flow/StateFlow đáp ứng được.
- CẤM xử lý navigation, toast/snackbar trực tiếp trong ViewModel bằng Android API.
- CẤM `!!`, catch rỗng, bỏ qua lỗi, hard-code string UI/màu/dimension; dùng resources/theme khi phù hợp.
- CẤM sửa schema Room mà không tăng version, migration và test migration tương ứng.
- CẤM thêm dependency, đổi Gradle/version, đổi public contract hay format toàn repo ngoài phạm vi yêu cầu.
- CẤM tự ý refactor code không liên quan hoặc thay đổi file sinh tự động.
- CẤM để Controller ghi Room database của Host, điều khiển YouTube SDK trực tiếp, hoặc tự suy luận queue local là đúng.
- CẤM mở WebSocket server trên interface Internet, tắt xác thực pairing, hoặc tin payload từ controller mà không validate.
- CẤM chuyển role khi player/server cũ chưa được giải phóng, hoặc chạy Host và Controller đồng thời trên cùng thiết bị.

## 6. Example code chuẩn

Ví dụ dùng `Task` domain model và `ObserveTasksUseCase`. Điều chỉnh package/model theo feature thực tế, không copy nguyên xi khi contract khác.

### `TaskListViewModel.kt`

```kotlin
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val observeTasks: ObserveTasksUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState.asStateFlow()

    init {
        observeTasks()
            .onStart { _uiState.update { it.copy(isLoading = true, errorMessageRes = null) } }
            .onEach { tasks ->
                _uiState.update { it.copy(isLoading = false, tasks = tasks) }
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                _uiState.update {
                    it.copy(isLoading = false, errorMessageRes = R.string.error_load_tasks)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: TaskListUiEvent) {
        when (event) {
            TaskListUiEvent.Retry -> refresh()
        }
    }

    private fun refresh() {
        // Gọi use case refresh riêng tại đây nếu feature có yêu cầu.
    }
}

data class TaskListUiState(
    val isLoading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    @StringRes val errorMessageRes: Int? = null,
)

sealed interface TaskListUiEvent {
    data object Retry : TaskListUiEvent
}
```

### `TaskListScreen.kt`

```kotlin
@Composable
fun TaskListRoute(
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TaskListScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun TaskListScreen(
    uiState: TaskListUiState,
    onEvent: (TaskListUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.errorMessageRes != null -> ErrorContent(
            message = stringResource(uiState.errorMessageRes),
            onRetry = { onEvent(TaskListUiEvent.Retry) },
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items(items = uiState.tasks, key = { it.id }) { task ->
                TaskRow(task = task)
            }
        }
    }
}
```

## 7. Definition of Done

- Code tuân thủ dependency direction và các quy tắc CẤM ở trên.
- Lên plan trước khi thực hiện
- Không có import/dependency dư thừa; string hiển thị được đưa vào resources khi áp dụng.
- Có unit test cho ViewModel/use case hoặc giải thích rõ vì sao chưa thể test.
- Chạy tối thiểu unit test liên quan; chạy build/lint khi thay đổi có phạm vi phù hợp.
- Báo cáo ngắn: plan đã thực hiện, file thay đổi, test đã chạy, và hạn chế còn lại (nếu có).
