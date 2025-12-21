plugins {
    java
    `maven-publish`
    // 若不需要胖包，直接移除Shadow插件，彻底避免报错
    // id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "org.xmas"
version = "1.0"

// Java 8 配置
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// 仓库源
repositories {
    mavenLocal()
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
    maven("https://maven.enginehub.org/repo/")
}

// 本地Jar依赖
dependencies {
    compileOnly(files("libs/spigot-1.8.8.jar"))
    compileOnly(files("libs/worldedit-bukkit-6.1.9.jar"))
}

// 普通Jar打包（核心，满足插件运行需求）
// Jar打包配置
tasks.jar {
    // 输出Jar文件名
    archiveFileName.set("XmasChristmasParty-${version}.jar")
    // 复制resources目录（plugin.yml）到Jar根目录
    from(sourceSets.main.get().resources)
    // 关键：设置重复文件策略为覆盖（解决duplicate报错）
    duplicatesStrategy = DuplicatesStrategy.INCLUDE // 或DuplicatesStrategy.REPLACE
    // 排除签名文件，避免冲突
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}


// 若需要Shadow胖包，取消注释并修改为适配版本
// tasks.shadowJar {
//     archiveName = "XmasChristmasParty-Shadow-${version}.jar" // 6.x版本用archiveName
// }

// 编译选项
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}

// 自动复制到服务器
tasks.register<Copy>("copyToServer") {
    val serverPluginDir = "C:/Users/27786/Desktop/MinecraftServer/1.8.8/plugins"
    from(tasks.jar.get().archiveFile)
    into(serverPluginDir)
    doLast {
        println("插件Jar已复制到：$serverPluginDir")
    }
}

// 构建依赖复制任务
tasks.build {
    dependsOn(tasks.named("copyToServer"))
}
