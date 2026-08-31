(function () {

    const TOKEN_KEY = "smoke_access_token";
    const USER_KEY = "smoke_user";
    const SAVED_USERNAME_KEY = "smoke_saved_username";

    /*
     * 保存原始 fetch。
     *
     * 后面页面里的 fetch("/latest")
     * 不需要一个个修改。
     */
    const originalFetch =
        window.fetch.bind(window);


    function getToken() {

        return sessionStorage.getItem(
            TOKEN_KEY
        );
    }


    function getUser() {

        const text =
            sessionStorage.getItem(
                USER_KEY
            );

        if (!text) {
            return null;
        }

        try {

            return JSON.parse(text);

        } catch (e) {

            return null;
        }
    }


    function saveLogin(token, user) {

        sessionStorage.setItem(
            TOKEN_KEY,
            token
        );

        sessionStorage.setItem(
            USER_KEY,
            JSON.stringify(user)
        );
    }


    function saveUser(user) {

        sessionStorage.setItem(
            USER_KEY,
            JSON.stringify(user)
        );
    }


    function clearSession() {

        sessionStorage.removeItem(
            TOKEN_KEY
        );

        sessionStorage.removeItem(
            USER_KEY
        );
    }


    /*
     * ==========================================
     * 退出登录
     * ==========================================
     *
     * 保留“记住的用户名”，
     * 但删除JWT和用户会话。
     */
    function logout() {

        clearSession();

        window.location.replace(
            "/login.html"
        );
    }


    /*
     * ==========================================
     * 页面登录检查
     * ==========================================
     */
    function requireLogin() {

        const token =
            getToken();

        if (!token) {

            window.location.replace(
                "/login.html"
            );

            return false;
        }

        return true;
    }


    /*
     * ==========================================
     * 判断公开认证接口
     * ==========================================
     */
    function isPublicAuthUrl(url) {

        return url.includes(
                "/api/auth/login"
            )
            ||
            url.includes(
                "/api/auth/register"
            );
    }


    /*
     * ==========================================
     * 是否为本系统请求
     * ==========================================
     */
    function isLocalRequest(url) {

        if (!url) {
            return false;
        }

        /*
         * 相对地址
         */
        if (url.startsWith("/")) {
            return true;
        }

        /*
         * localhost完整地址
         */
        try {

            const parsed =
                new URL(
                    url,
                    window.location.origin
                );

            return parsed.origin
                === window.location.origin;

        } catch (e) {

            return false;
        }
    }


    /*
     * ==========================================
     * 全局Fetch拦截
     * ==========================================
     *
     * 页面原来：
     *
     * fetch("/api/alarm/list")
     *
     * 会自动变成：
     *
     * Authorization: Bearer JWT
     *
     * 所以index.html已有大量fetch，
     * 不需要全部手动修改。
     */
    window.fetch =
        async function (
            input,
            options = {}
        ) {

            let url;

            if (typeof input === "string") {

                url = input;

            } else if (
                input
                && input.url
            ) {

                url = input.url;

            } else {

                url = "";
            }


            /*
             * 登录和注册本身不需要JWT。
             */
            const publicAuth =
                isPublicAuthUrl(
                    url
                );


            const local =
                isLocalRequest(
                    url
                );


            const token =
                getToken();


            /*
             * 本系统的受保护请求，
             * 自动添加Authorization。
             */
            if (
                local
                && !publicAuth
                && token
            ) {

                const headers =
                    new Headers(
                        options.headers
                        || {}
                    );


                headers.set(
                    "Authorization",
                    "Bearer " + token
                );


                options = {
                    ...options,
                    headers: headers
                };
            }


            const response =
                await originalFetch(
                    input,
                    options
                );


            /*
             * ==================================
             * JWT失效
             * ==================================
             *
             * 登录接口自身返回401时，
             * 不能自动跳转。
             */
            if (
                response.status === 401
                && !publicAuth
            ) {

                clearSession();


                /*
                 * 当前不是登录页才跳转，
                 * 防止循环。
                 */
                if (
                    !window.location.pathname
                        .endsWith(
                            "/login.html"
                        )
                ) {

                    window.location.replace(
                        "/login.html"
                    );
                }
            }


            return response;
        };


    /*
     * 暴露给其他页面使用。
     */
    window.SmokeAuth = {

        TOKEN_KEY,
        USER_KEY,
        SAVED_USERNAME_KEY,

        getToken,
        getUser,

        saveLogin,
        saveUser,

        clearSession,

        requireLogin,

        logout
    };

})();