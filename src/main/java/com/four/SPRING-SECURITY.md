# Spring security

[Spring Security官网][https://docs.spring.io/spring-security/site/docs/5.4.2/reference/html5/#servlet-architecture]

![avatar](https://github.com/rbmonster/learning-note/blob/master/src/main/java/com/four/picture/spring-security.jpg)


## 认证 Authentication
几个登陆认证重要的组件：

SecurityContextHolder：Spring Security存储安全身份验证者详细信息的位置。
> 使用ThreadLocal实现

SecurityContext：从SecurityContextHolder获得，并包含当前经过身份验证的用户的身份验证。

Authentication：可以是AuthenticationManager的输入，以提供用户提供的用于身份验证的凭据或来自SecurityContext的当前用户。

GrantedAuthority：授予身份验证主体的权限（即角色，作用域等）

AuthenticationManager：定义Spring Security的过滤器如何执行身份验证的API。

ProviderManager：AuthenticationManager的最常见实现。

AuthenticationProvider：由ProviderManager用于执行特定类型的身份验证。

带AuthenticationEntryPoint的请求凭据：用于从客户端请求凭据（即重定向到登录页面，发送WWW身份验证响应等）

AbstractAuthenticationProcessingFilter：用于验证的基本过滤器。这也为高级身份验证流程以及各个部分如何协同工作提供了一个好主意。

UserDetailsService: spring security 默认提供的用户密码登陆接口。




## 授权 Authorization

### 基于注解
使用注解标注方法是否有权限
```
@PreAuthorize("hasRole('USER')")
@PostFilter("hasPermission(filterObject, 'read') or hasPermission(filterObject, 'admin')")
public List<Contact> getAll();
```

### 使用配置添加权限
另一种做法是在继承WebSecurityConfigurerAdapter时，将url及对应的权限全部load进 httpSecurity中
```
ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry urlRegistry = http.authorizeRequests();
Map<String, String> resourcePermissions = permissionService.getResourcePermissions();
for (Map.Entry<String, String> entry : resourcePermissions.entrySet()) {
    urlRegistry = urlRegistry.antMatchers(entry.getKey()).hasAnyAuthority(entry.getValue());
}
```


### 总结
使用配置添加权限，相当于需要一大部分的工作量配置角色对应的权限。
- 好处：数据库显示前台表统一管理。
- 坏处：配置工作量大，每个角色均需要配置。新增加接口需要新增加数据库记录。

基于注解配置权限
- 好处：开发人员即可配置权限，配置简单。
- 缺点：权限散落在各个接口中，不利于统一管理。



## 继承WebSecurityConfigurerAdapter的demo
```

@EnableWebSecurity
@Slf4j
public class WebSecurityConfigurer extends WebSecurityConfigurerAdapter {


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
        // 设置从不创建session，正常基于jwt认证才会如此设置
        .sessionManagement().sessionCreationPolicy( SessionCreationPolicy.STATELESS ).and()
        // 设置异常处理返回
        .exceptionHandling().authenticationEntryPoint( restAuthenticationEntryPoint ).and()
        // 设置不拦截的url请求
        .authorizeRequests()
        .antMatchers("/api/v1/admin/login/**").permitAll()
        
        http.authorizeRequests()
            .anyRequest().authenticated().and()
            // 添加拦截器
            .addFilterBefore(tokenAuthenticationFilter, BasicAuthenticationFilter.class);
        // 禁止跨服务请求
        http.csrf().disable();
    }
}
```

## Session 与 Cookies 认证
### 认证过程
很多时候我们都是通过 SessionID 来实现特定的用户，SessionID 一般会选择存放在 Redis 中。举个例子：用户成功登陆系统，然后返回给客户端具有 SessionID 的 Cookie，当用户向后端发起请求的时候会把 SessionID 带上，这样后端就知道你的身份状态了。关于这种认证方式更详细的过程如下：

1. 用户向服务器发送用户名和密码用于登陆系统。
2. 服务器验证通过后，服务器为用户创建一个 Session，并将 Session信息存储 起来。
3. 服务器向用户返回一个 SessionID，写入用户的 Cookie。
4. 当用户保持登录状态时，Cookie 将与每个后续请求一起被发送出去。
5. 服务器可以将存储在 Cookie 上的 Session ID 与存储在内存中或者数据库中的 Session 信息进行比较，以验证用户的身份，返回给用户客户端响应信息的时候会附带用户当前的状态。


另外，Spring Session提供了一种跨多个应用程序或实例管理用户会话信息的机制。

### Cookie 无法防止CSRF攻击
在登陆了网银后，点击误导的超链接，导致跨服务请求成功。
```
<a src=http://www.mybank.com/Transfer?bankId=11&money=10000>科学理财，年盈利率过万</>
```
进行Session 认证的时候，我们一般使用 **Cookie 来存储 SessionId**,当我们登陆后后端生成一个SessionId放在Cookie中返回给客户端，服务端通过Redis或者其他存储工具记录保存着这个Sessionid，客户端登录以后每次请求都会带上这个SessionId，服务端通过这个SessionId来标示你这个人。如果别人通过 cookie拿到了 SessionId 后就可以代替你的身份访问系统了。


![avatar](https://github.com/rbmonster/learning-note/blob/master/src/main/java/com/four/picture/Cookies-attack.jpg)

可以使用token认证的方式避免误点攻击链接导致的跨服务请求问题。
> 基于token 认证经常将认证凭证存储在local storage中，在请求的时候前端再动态添加凭证到请求中。因此误点的外部链接无法添加token到转发的请求中。

## 基于token认证 JWT

Json web token (JWT), 是为了在网络应用环境间传递声明而执行的一种基于JSON的开放标准（(RFC 7519).该token被设计为紧凑且安全的，特别适用于分布式站点的单点登录（SSO）场景。JWT的声明一般被用来在身份提供者和服务提供者间传递被认证的用户身份信息，以便于从资源服务器获取资源，也可以增加一些额外的其它业务逻辑所必须的声明信息，该token也可直接被用于认证，也可被加密。


### Session认证暴露的缺点
内存开销大： 每个用户经过我们的应用认证之后，我们的应用都要在服务端做一次记录，以方便用户下次请求的鉴别，通常而言session都是保存在内存中，而随着认证用户的增多，服务端的开销会明显增大。

分布式场景限制：在分布式的应用上，相应的限制了负载均衡器的能力。这也意味着限制了应用的扩展能力。

CSRF跨服务请求问题: 因为是基于cookie来进行用户识别的, cookie如果被截获，用户就会很容易受到跨站请求伪造的攻击。




### 基于token 认证流程
基于token的鉴权机制类似于http协议也是无状态的，它不需要在服务端去保留用户的认证信息或者会话信息。这就意味着基于token认证机制的应用不需要去考虑用户在哪一台服务器登录了，这就为应用的扩展提供了便利。

流程上是这样的：

1. 用户使用用户名密码来请求服务器
2. 服务器进行验证用户的信息
3. 服务器通过验证发送给用户一个token
4. 客户端存储token，并在每次请求时附送上这个token值
5. 服务端验证token值，并返回数据
这个token必须要在每次请求时传递给服务端，它应该保存在请求头里， 另外，服务端要支持CORS(跨来源资源共享)策略，一般我们在服务端这么做就可以了Access-Control-Allow-Origin: *。



### jwt 组成
JWT 由 3 部分构成:
1. Header :描述 JWT 的元数据。定义了生成签名的算法以及 Token 的类型。
2. Payload（负载）:用来存放实际需要传递的数据
3. Signature（签名）：服务器通过Payload、Header和一个密钥(secret)使用 Header 里面指定的签名算法（默认是 HMAC SHA256）生成。

> secret是保存在服务器端的，jwt的签发生成也是在服务器端的，secret就是用来进行jwt的签发和jwt的验证，所以，它就是你服务端的私钥，在任何场景都不应该流露出去。一旦客户端得知这个secret, 那就意味着客户端是可以自我签发jwt了。
```
 public String generateToken(String subject) {
        return Jwts.builder()
                //  设置发行人即 签发的 APP
                .setIssuer( APP_NAME )
                // 存放实际传递数据
                .setSubject(subject)
                // token 生成时间
                .setIssuedAt(new Date())
                // 过期时间
                .setExpiration(generateExpirationDate())
                // SIGNATURE_ALGORITHM加密算法    SECRET加盐字段 进行组合加密
                .signWith( SIGNATURE_ALGORITHM, SECRET )
                // 生成token
                .compact();
 }
```

### 总结
优点
1. 因为json的通用性，所以JWT是可以进行跨语言支持的，像JAVA,JavaScript,NodeJS,PHP等很多语言都可以使用。
2. 因为有了payload部分，所以JWT可以在自身存储一些其他业务逻辑所必要的非敏感信息。
3. 便于传输，jwt的构成非常简单，字节占用很小，所以它是非常便于传输的。
4. 它不需要在服务端保存会话信息, 所以它易于应用的扩展

- 安全相关
1. 不应该在jwt的payload部分存放敏感信息，因为该部分是客户端可解密的部分。
2. 保护好secret私钥，该私钥非常重要。
3. 如果可以，请使用https协议

## spring security + JWT
- 继承WebSecurityConfigurerAdapter类，重写config 方法，定制一些httpSecurity的规则。
> 对于前后端分离的开发模式，需开放一个签发认证的url接口，而其他url接口根据业务要求，可以直接屏蔽返回未认证。
```
 @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors(withDefaults())
                // 禁用 CSRF
                .csrf().disable()
                .authorizeRequests()
                // swagger
                .antMatchers(SecurityConstants.SWAGGER_WHITELIST).permitAll()
                // 登录接口
                .antMatchers(HttpMethod.POST, SecurityConstants.LOGIN_WHITELIST).permitAll()
                // 指定路径下的资源需要验证了的用户才能访问
                .antMatchers(SecurityConstants.FILTER_ALL).authenticated()
                .antMatchers(HttpMethod.DELETE, SecurityConstants.FILTER_ALL).hasRole("ADMIN")
                // 其他都放行了
                .anyRequest().permitAll()
                .and()
                //添加自定义Filter
                .addFilter(new JwtAuthorizationFilter(authenticationManager(), stringRedisTemplate))
                // 不需要session（不创建会话）
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
                // 授权异常处理
                .exceptionHandling().authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                .accessDeniedHandler(new JwtAccessDeniedHandler());
        // 防止H2 web 页面的Frame 被拦截
        http.headers().frameOptions().disable();
    }
```

- 登陆接口：验证用户名密码签发token
```
 @GetMapping
    private String toLogin(HttpServletRequest request, String username, String password) {
        // 登录成功
        if (password.equals("112233")) {
            String token = tokenHelper.generateToken(username+"~role");
            // 登陆成功
            return token;
        }
        return "认证失败";
    }
```

- 添加过滤器，用于验证token及设置通过spring security认证。
```
   @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String userInfo;
        // 获取请求头的认证token
        String authToken = tokenHelper.getToken(request);

        if (authToken != null) {
            // get username from token
            userInfo = tokenHelper.getUserInfoFromToken(authToken);
            System.out.println(userInfo);
            if (userInfo != null) {
                if (tokenHelper.validateToken(authToken)) {
                    String[] userInfos = userInfo.split("~");

                    String refreshToken = tokenHelper.refreshToken(authToken);
                    response.addHeader("refreshtoken", refreshToken);
                    // create authentication
                    String userName = userInfos[0];
                    List<GrantedAuthority> authorities = buildAuthorities(userName);
                    // spring security 设置存在相关的token信息认证通过
                    TokenBasedAuthentication authentication = new TokenBasedAuthentication(new AccountCredentials(userName, authorities));
                    authentication.setToken(authToken);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
            ...
        }

        chain.doFilter(request, response);
    }
```

## JWT token 常见问题
### 注销登录等场景下 token 处理
需要处理token的场景：
1. 退出登录;
2. 修改密码;
3. 服务端修改了某个用户具有的权限或者角色；
4. 用户的帐户被删除/暂停。
5. 用户由管理员注销；


解决方案：
1. 存储黑名单token，如redis + token过期时间存储黑名单。
    - redis的数据结构可以使用string + 过期时间，把token 当key，value使用状态表示。
    - redis 使用zset，使用过期时间当score，value为jwt token, 使用zrangeByscore 过滤是否还存在过期key, zrank key member 获取元素是否存在。
2. 设置较短的token过期时间，但是会导致用户频繁登陆，体验不好。


### 过期token 的续签问题
1. token有效期延长。适合安全度不高的系统。
2. 用户登录返回两个 token。一个用于登陆一个用于续签。
    - 一个是 acessToken ，它的过期时间 token 本身的过期时间比如半个小时
    - 一个是 refreshToken 它的过期时间更长一点比如为1天。
> 该方案的不足是：
> 1. 需要客户端来配合； 
> 2. 用户注销的时候需要同时保证两个 token 都无效；
> 3. 重新请求获取 token 的过程中会有短暂 token 不可用的情况（可以通过在客户端设置定时器，当accessToken 快过期的时候，提前去通过 refreshToken 获取新的accessToken）。



## 相关资料
- [认证基础知识](https://github.com/Snailclimb/JavaGuide/blob/master/docs/system-design/authority-certification/basis-of-authority-certification.md)
- [jwt认证](https://www.jianshu.com/p/576dbf44b2ae)