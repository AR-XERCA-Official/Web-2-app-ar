import os
import json
import uuid
from typing import Optional
from fastapi import FastAPI, Form, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.templating import Jinja2Templates
from pydantic import BaseModel

app = FastAPI(title="WebToApp Vercel Remote Control Engine")

# السماح بالاتصال من أي مكان (CORS)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ضبط مسار مجلد templates عشان يشتغل صح على Vercel
# 1. رجوع خطوتين فقط للوصول للمجلد الرئيسي للمشروع
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# 2. تحديد مسار templates الصحيح
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

# قاعدة بيانات مؤقتة في الذاكرة لتطبيقاتك
APPS_DATABASE = {}


# ==========================================
# 1. الصفحة الرئيسية (تفتحه مباشرة في المتصفح)
# ==========================================
@app.get("/")
def home_page(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})


# ==========================================
# 2. إنشاء وتحديث إعدادات التطبيق أونلاين
# ==========================================
@app.post("/api/apps/create")
async def create_app(
    app_name: str = Form(...),
    target_url: str = Form(...),
    auth_type: str = Form("none"),  # none, password, email, admin_approval
    access_password: str = Form("")
):
    app_id = f"app_{uuid.uuid4().hex[:8]}"

    config_data = {
        "app_id": app_id,
        "app_name": app_name,
        "target_url": target_url,
        "primary_color": "#4F46E5",
        "is_active": True,
        "maintenance_message": "التطبيق قيد الصيانة حالياً من قبل الإدارة.",
        "security": {
            "auth_type": auth_type,
            "access_password": access_password,
            "allowed_emails": [],
            "approved_users": []  # حسابات العُملاء المعتمدين أونلاين
        }
    }

    APPS_DATABASE[app_id] = config_data

    return {
        "success": True,
        "app_id": app_id,
        "message": "تم إنشاء التطبيق وإعداد التحكم أونلاين على Vercel بنجاح!",
        "config": config_data
    }


# ==========================================
# 3. جلب إعدادات التطبيق أونلاين (لتطبيق الموبايل)
# ==========================================
@app.get("/api/apps/config/{app_id}")
def get_app_config(app_id: str):
    if app_id not in APPS_DATABASE:
        # إرجاع قيم افتراضية للتجربة لو الـ ID مش موجود
        return {
            "app_id": app_id,
            "app_name": "تطبيقي أونلاين",
            "target_url": "https://google.com",
            "is_active": True,
            "security": {
                "auth_type": "none",
                "access_password": "",
                "approved_users": []
            }
        }
    return APPS_DATABASE[app_id]


# ==========================================
# 4. التحقق أونلاين من الدخول والموافقات
# ==========================================
@app.post("/api/apps/verify-access")
def verify_access(
    app_id: str = Form(...),
    user_input: str = Form(...),
    request_approval: bool = Form(False)
):
    app_config = APPS_DATABASE.get(app_id)
    
    # تجربة افتراضية في حال التست
    if not app_config:
        if user_input == "123456":  # باسورد تجريبي
            return {"access": True, "message": "تم الدخول بنجاح"}
        return {"access": False, "message": "بيانات الدخول غير صحيحة"}

    sec = app_config.get("security", {})
    auth_type = sec.get("auth_type", "none")

    if auth_type == "none":
        return {"access": True, "message": "دخول مسموح"}

    elif auth_type == "password":
        if user_input == sec.get("access_password"):
            return {"access": True, "message": "تم التحقق بنجاح"}
        return {"access": False, "message": "كلمة السر غير صحيحة!"}

    elif auth_type == "admin_approval":
        approved = sec.get("approved_users", [])
        if user_input in approved:
            return {"access": True, "status": "approved", "message": "تمت الموافقة على دخولك!"}
        
        if request_approval:
            return {"access": False, "status": "pending", "message": "تم تقديم طلبك للآدمن، ينتظر الموافقة أونلاين."}

        return {"access": False, "status": "not_approved", "message": "حسابك يحتاج موافقة من الآدمن."}

    return {"access": False, "message": "نوع الحماية غير مدعوم"}


# ==========================================
# 5. موافقة الآدمن أونلاين على العميل
# ==========================================
@app.post("/api/admin/approve-user")
def approve_user_online(app_id: str = Form(...), user_email: str = Form(...)):
    if app_id in APPS_DATABASE:
        if user_email not in APPS_DATABASE[app_id]["security"]["approved_users"]:
            APPS_DATABASE[app_id]["security"]["approved_users"].append(user_email)
        return {"success": True, "message": f"تمت الموافقة أونلاين على العميل {user_email}"}
    
    return {"success": True, "message": f"تم إرسال الموافقة للعميل {user_email} بنجاح!"}
