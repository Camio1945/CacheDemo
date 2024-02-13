# CacheDemo

用于演示缓存相关的一些问题，如：缓存穿透、缓存击穿、缓存雪崩、数据一致性等。

# 如何使用

1. 安装 MySQL 8.0，并用 root 用户执行 `cache_demo_init.sql` 文件。

2. 安装 Redis 7.2 （其他版本也行），并修改所有的 `application.yml` 文件中的 `spring.data.redis.host` 等配置。

3. 使用 IntelliJ IDEA 打开项目，然后运行各子包的单元测试，用 Java 21。

# 提醒

某些单元测试会清空 Redis 的 6 号数据库，所以请不要在生产环境运行单元测试。

# 子包说明

注：每个版本仅列出一个问题（或同一类型的多个问题），后续版本会解决上一个版本的问题。

### v1

存在缓存穿透的问题。ID 为 100001 的商品不存在，但是每次查询都会去查询数据库，当频繁访问时，会导致数据库压力增大。

### v2

解决了缓存穿透的问题。解决办法是：缓存空值（不推荐使用布隆过滤器，因为有一定的概率会误判）。

存在的问题：高并发访问一个 key 时，如果缓存中不存在，不管数据库中是否存在，都会导致同一时刻会有多个请求查询数据库，导致数据库压力增大。

其中一个场景就是缓存击穿问题：数据本来在缓存中存在，在数据库中也存在，但是由于缓存过期了，这时高并发访问时，会多次查询数据库。

缓存穿透和缓存击穿的共性与区别：共性是它们都是针对同一个 key 做大量的访问；区别是缓存穿透是查询数据库中不存在的数据，缓存击穿是查询数据库中存在的数据。

注：缓存击穿问题并没有缓存穿透问题严重。

### v3

解决了缓存击穿的问题。解决办法是：使用单飞模式，多个线程同时需要访问数据库时，增加同步机制，只允许其中一个线程查询数据库，其他线程共享前面线程的查询结果。

存在的问题：商品 id 是整数，而且是自增的，商品数量有限，比如我的例子里面是 10 万个，而整数的范围是 21 亿多，如果有恶意用户依次访问这 21 亿不存在的商品 id ，由于我们为了解决缓存穿透问题而缓存了空值，那么这 21 亿不存在的 id 也会在缓存中存在，从而导致内存暴增。21 亿空商品，如果是用 SpringCache 操作的 redis ，每个空对象的值也会占用 64B 的空间，加上键的空间，21 亿个键值对大约需要 300G~500G 的内存。

### v4

解决了访问大量不存在的商品时造成的内存暴增问题。解决办法是（不同业务的解决办法不一样，这里仅仅只是提供一个思路）：项目启动时，查询商品表中的最大 id，然后加上一个保险值（比如 1000），得到的结果存入静态变量中， 每次新增商品时，更新这个最大的 id，也需要加上一个保险值（防止集群中的变量来不及更新）。 每次查询商品时，如果商品 id 大于这个上限值，则直接抛异常。 一般来说，正常的业务是不会访问不存在的 id 的，对于恶意请求，抛异常也无所谓。

存在的问题：缓存雪崩问题。如果过期时间是固定的，在某一时刻并发量很大，把大量的数据从数据库加载到缓存中，这时它们的过期时间是相同了，到了过期时间点，大量的 key 同时失效，都要从数据库中加载，导致数据库的压力陡增。

### v5

解决了缓存雪崩问题。解决办法是：设置过期时间时，给一个随机的偏移量。虽然是在同一时间进入的缓存，但是失效时间大概率是不一样的，把单一时间点的压力分散到了不同的时间点。

存在的问题：目前无法查看缓存命中率是多少。

### v6

解决了无法查看缓存命中率的问题。解决办法是：直接调用 redis 的命令，获取 keyspace_hits 和 keyspace_misses 的值，然后计算命中率。
注意，这种办法只能查看总体的命中率，无法查看单个 key 的命中率。如果需要查看单个 key 的命中率，可以修改 application.properties 配置文件中的日志级别为 `logging.level.org.springframework.cache=trace` ，然后分析日志文件，这时也可以顺便统计出来哪些是热点 key。

存在的问题：目前为止一直是只用了商品 id 来作为缓存的 key，但是其他场景下，比如用户表的手机号和邮箱都是唯一的，或者商品表商家 id 和商品名称联合起来也是唯一的，我们可能需要通过这些唯一字段来查询缓存。

### v7

解决了不能通过 id 之外的其他唯一索引查询缓存的问题。解决办法是：先通过唯一索引查询到 id，再通过 id 查询缓存。

这样做的优点是：

1. 可以节省空间，因为不需要为其他唯一字段再建立从字段到对象的缓存（如 account -> User）。
2. 当需要更新时，只需要更新 id 对应的缓存，而不需要更新其他唯一字段对应的缓存。

这样做的缺点是：

1. 需要多一次查询。
2. 在清除缓存时，Spring Cache 提供的注解已经不能满足需求了，需要自己写代码来实现。

总体来说，利大于弊。

存在的问题：目前为止只只是缓存了单行记录，没有对列表进行缓存。

### v8

解决了不能缓存列表（这里指的是不易变、且数据相对较少的列表）的问题。
以某个店铺下的商品为例，一个店铺下的商品数量一般不会很多，而且不会频繁变动，所以可以缓存这个列表。

解决办法是：每个店铺有一个缓存的 key ，值是这个店铺下的商品 id 列表(使用 redis 的 sorted set)。要查询某个店铺下的商品时，先查询这个 key ，得到商品 id 列表，然后再一次性根据多个 id 查询多个商品详情。

存在的问题：还没有缓存过易变的、数量较多的列表，比如微博。

### v9

解决了不能缓存易变的、数量较多的列表的问题。

解决办法：以微博为例，一个用户可能会发很多条微博，假设是 5000 条，但是一般访问最多的只有最近的 50 条，所以可以缓存最近的 50 条微博。依旧使用 redis 的 sorted set 来解决，假设一个 sorted set 里面存了 50 个元素，从左到右依次是从旧到新的数据，那么：

1. 从右边增加一个新元素时，从左边删除一个旧元素

2. 从任何地方删除一个旧元素时，从左边补充一个更旧的元素

这样可以保证 sorted set 的大小不会超过指定值（比如 50）。




















