# MarisGuard Wiki

Trang này viết theo hướng dùng thật, không phải giới thiệu cho đẹp. Nếu anh chỉ cần biết plugin đang làm gì, cấu hình ở đâu và nên chỉnh cái gì trước, đọc trang này là đủ.

## MarisGuard hiện có gì

MarisGuard hiện đang gộp 4 phần:

- `LoadingScreenRemover`
- `RayTraceAntiXray`
- `MarisEsp`
- `Player Visibility Raytrace`

Nó không còn `AntiFreeCam` nữa. Phần đó đã bị gỡ khỏi code và khỏi config.

## Plugin này dùng để làm gì

### 1. LoadingScreenRemover

Phần này giảm tình trạng client bị kẹt ở màn hình loading terrain khi đổi world hoặc khi server gửi thêm packet `RESPAWN` trong mấy case nhạy cảm.

Config liên quan:

```yml
debug: false
track-ticks: 80
death-bypass-ticks: 200
aggressive-same-environment: true
```

Ý nghĩa ngắn:

- `debug`: bật log debug. Chỉ nên bật khi đang soi lỗi.
- `track-ticks`: thời gian plugin theo dõi thay đổi world.
- `death-bypass-ticks`: nới thời gian bypass cho lúc chết rồi respawn.
- `aggressive-same-environment`: xử lý mạnh tay hơn với các lần đổi world mà environment giống nhau.

Nếu server đang ổn thì để nguyên mấy giá trị này.

### 2. RayTraceAntiXray

Đây là phần anti-xray kiểu raytrace. Nó không phải kiểu obfuscate block đơn giản, mà sẽ quyết định block nào nên lộ cho client dựa trên line-of-sight.

Config tổng:

```yml
settings:
  anti-xray:
    update-ticks: 4
    ms-per-ray-trace-tick: 75
    ray-trace-threads: 2
```

Ý nghĩa:

- `update-ticks`: chu kỳ update.
- `ms-per-ray-trace-tick`: thời gian giữa các đợt raytrace.
- `ray-trace-threads`: số worker xử lý raytrace.

Nếu ưu tiên mượt server thì đừng tăng bừa `ray-trace-threads`. Với Folia và server đông người, tăng thread không phải lúc nào cũng tốt.

Config theo world:

```yml
world-settings:
  default:
    anti-xray:
      ray-trace: true
      ray-trace-third-person: false
      ray-trace-distance: 32.0
      rehide-blocks: false
      rehide-distance: .inf
      max-ray-trace-block-count-per-chunk: 24
      ray-trace-blocks: []
```

Mấy dòng cần quan tâm:

- `ray-trace`: bật tắt anti-xray ở world đó.
- `ray-trace-distance`: khoảng cách raytrace block.
- `ray-trace-third-person`: có trace thêm góc nhìn third person hay không.
- `max-ray-trace-block-count-per-chunk`: giới hạn số block xử lý trong mỗi chunk.

Nếu cảm giác block hiện quá chậm thì thường nhìn trước vào:

- `ray-trace-distance`
- `update-ticks`
- `ms-per-ray-trace-tick`

### 3. MarisEsp

Phần này xử lý block masking và mấy phiên bait check để bắt ESP.

Config chính:

```yml
reveal-radius: 30
refresh-period-ticks: 10
mask-material: AIR
```

Ý nghĩa:

- `reveal-radius`: bán kính lộ block quanh người chơi.
- `refresh-period-ticks`: tốc độ refresh vùng reveal.
- `mask-material`: block dùng để che phía client. Hiện tại để `AIR`.

Nếu muốn block lộ nhanh hơn khi người chơi tiến gần:

- tăng `reveal-radius`
- giảm `refresh-period-ticks`

Blacklist world:

```yml
blacklist-worlds:
  - world_the_end
  - spawn
  - afk
  - duels
```

World nằm trong đây sẽ bị tắt anti-ESP / anti-xray tương ứng theo logic hiện tại.

### 4. ESP auto-check

Phần này dùng bait entity / session check để bắt người chơi đang dùng ESP.

Config:

```yml
esper:
  enabled: true
  punishable: true
  punishment-delay-in-seconds: 0
  max-violations: 7
  punishment-commands:
    - 'tempban %player% 30m use esp'
  duration-seconds: 15
  spawn-distance: 10.5
  forward-offset: 2.0
  trigger-distance: 2.0
  auto-check:
    enabled: true
    interval-ticks: 60
    max-players-per-cycle: 2
    cooldown-seconds: 90
    max-active-sessions: 4
    require-survival: true
    max-y: 29
```

Nên chú ý:

- `max-players-per-cycle`
- `max-active-sessions`
- `interval-ticks`

Ba dòng này ảnh hưởng khá rõ tới tải server. Nếu server đông người mà muốn an toàn hơn thì giảm số session song song trước.

### 5. Player Visibility Raytrace

Phần này không liên quan tới tablist. Nó chỉ chặn việc client thấy entity player khi mọi tia nhìn đều bị block cản.

Config:

```yml
player-visibility-raytrace:
  enabled: true
  worlds:
    - world
  max-distance: 24.0
  check-period-ticks: 20
  max-targets-per-tick: 2
  candidate-refresh-ticks: 10
```

Ý nghĩa:

- `worlds`: chỉ chạy ở các world này.
- `max-distance`: bán kính tối đa để xét hide/show player.
- `check-period-ticks`: chu kỳ check.
- `max-targets-per-tick`: giới hạn số target xử lý mỗi tick.
- `candidate-refresh-ticks`: chu kỳ refresh candidate list.

Ví dụ `max-distance: 24.0` có nghĩa là chỉ các player trong phạm vi 24 block mới bị đưa vào luồng xét raytrace này.

Phần này hiện cũng có chặn player tàng hình. Nếu target đang invisible hoặc viewer không `canSee(target)` thì MarisGuard sẽ không cố lộ entity đó nữa.

## Storage

Violation storage của phần ESP hiện hỗ trợ:

- `sqlite`
- `mysql`

Config:

```yml
storage:
  type: sqlite
  sqlite:
    file: violations.db
  mysql:
    host: 127.0.0.1
    port: 3306
    database: antiesp
    username: root
    password: ''
    properties: useSSL=false&characterEncoding=utf8&serverTimezone=UTC
  pool:
    maximum-pool-size: 4
    minimum-idle: 1
    connection-timeout-ms: 10000
```

Nếu chỉ chạy một server nhỏ hoặc vừa thì `sqlite` là đủ. Nếu muốn đồng bộ hoặc kiểm soát DB riêng thì mới chuyển sang `mysql`.

## Cấu hình gợi ý

### Nếu ưu tiên mượt server

- giữ `ray-trace-threads: 2`
- giữ `ms-per-ray-trace-tick` tương đối cao
- không tăng `max-active-sessions` bừa
- hạn chế world chạy `player-visibility-raytrace`

### Nếu ưu tiên phát hiện nhanh hơn

- tăng `reveal-radius`
- giảm `refresh-period-ticks`
- tăng `player-visibility-raytrace.max-distance`
- giảm `check-period-ticks`

Đổi lại, CPU và packet cost sẽ tăng. Cái này không có cấu hình “vừa mạnh vừa miễn phí”.

## Version check khi khởi động

Lúc plugin enable xong, MarisGuard sẽ check release mới nhất từ GitHub repo:

- nếu đang là bản mới nhất:
  - `You are currently using the latest version (x.x.x)`
- nếu có bản mới hơn:
  - `The new version has been released (x.x.x)`
- nếu check lỗi:
  - `Unable to check for the new version.`

Phần này chỉ log ngắn, không in stacktrace dài.

## Build

Repo đang được tách dần theo kiểu đa version:

- `api/`
- `core/`
- `nms-v1_20/`
- `nms-v1_21/`
- `nms-v26_1_2/`

Build:

```powershell
./gradlew :core:build "-PmarisTarget=paper-1.20"
./gradlew :core:build "-PmarisTarget=paper-1.21"
./gradlew :core:build "-PmarisTarget=paper-26_1_2"
```

Artifact hiện đang phát hành theo tên:

```text
MarisGuard-1.0.jar
```

## Tình trạng hiện tại của source

Repo đã tách được một phần bridge version-specific, nhưng chưa xong hoàn toàn. `playertrace` và một phần `raytrace packet send` đã có bridge riêng. Một số chỗ NMS lớn hơn vẫn đang được kéo dần ra khỏi `core`.

Nói ngắn: repo đã đi đúng hướng, nhưng chưa phải dạng “đa version hoàn toàn sạch” ở mọi module.

## Nếu cần bắt đầu chỉnh config thì nên sửa gì trước

Thứ tự thực dụng:

1. `blacklist-worlds`
2. `world-settings.<world>.anti-xray.ray-trace`
3. `ray-trace-distance`
4. `reveal-radius`
5. `refresh-period-ticks`
6. `player-visibility-raytrace.max-distance`
7. `esper.auto-check.*`

Đó là mấy dòng dễ tạo khác biệt rõ nhất.
