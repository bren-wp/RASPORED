import {defineConfig,devices} from "@playwright/test";
export default defineConfig({
  testDir:"./tests",
  use:{baseURL:"http://127.0.0.1:8080",trace:"retain-on-failure"},
  webServer:{command:"php -S 127.0.0.1:8080 -t public",url:"http://127.0.0.1:8080",reuseExistingServer:!process.env.CI},
  projects:[
    {name:"mobile-375",use:{viewport:{width:375,height:812}}},
    {name:"mobile-390",use:{viewport:{width:390,height:844}}},
    {name:"tablet",use:{viewport:{width:768,height:1024}}},
    {name:"desktop-1440",use:{viewport:{width:1440,height:1000}}},
    {name:"desktop-1920",use:{viewport:{width:1920,height:1080}}}
  ]
});
